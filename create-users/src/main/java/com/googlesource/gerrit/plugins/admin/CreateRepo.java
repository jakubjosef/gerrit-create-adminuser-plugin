package com.googlesource.gerrit.plugins.admin;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.googlesource.gerrit.plugins.admin.AclUtil.grant;
import static com.googlesource.gerrit.plugins.admin.AclUtil.rule;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Die;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.*;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.pgm.init.api.*;
import com.google.gerrit.reviewdb.client.*;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.*;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.*;
import org.apache.commons.validator.routines.EmailValidator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;


public class CreateRepo implements InitStep {

    private final ConsoleUI ui;
    private final InitFlags flags;
    private final String pluginName;
    private final AllProjectsConfig allProjectsConfig;
    private SchemaFactory<ReviewDb> dbFactory;
    private String message;

    private Injector injector;

    /* Can't be injected */
    private final AllProjectsName allProjectsName = new AllProjectsName("All-Projects");
    private final PersonIdent serverUser = new PersonIdent("admin", "admin@example.com");

    @Inject(optional = true)
    private GitRepositoryManager mgr;

/*    @Inject
    protected ProjectCache projectCache;*/

    private SitePaths site;
    private Config cfg;
    private PluginConfigFactory pluginCfg;

    private AccountGroup batchAccount;
    private AccountGroup adminAccount;

    private GroupReference admin;

    private GroupReference batch;
    private GroupReference anonymous;
    private GroupReference registered;
    private GroupReference owners;

    @Inject
    CreateRepo(@PluginName String pluginName, ConsoleUI ui,
               AllProjectsConfig allProjectsConfig,
               InitFlags flags) {
        this.pluginName = pluginName;
        this.allProjectsConfig = allProjectsConfig;
        this.flags = flags;
        this.ui = ui;
    }

    @Inject(optional = true)
    void set(SchemaFactory<ReviewDb> dbFactory) {
        this.dbFactory = dbFactory;
    }

/*    @Inject
    CreateRepo(@PluginName String pluginName,
               ConsoleUI ui,
               AllProjectsConfig allProjectsConfig,
               InitFlags flags,
               AllProjectsName allProjectsName,
               GitRepositoryManager mgr,
               @GerritPersonIdent PersonIdent serverUser) {
        this.pluginName = pluginName;
        this.allProjectsConfig = allProjectsConfig;
        this.flags = flags;
        this.ui = ui;
        this.mgr = mgr;
        this.allProjectsName = allProjectsName;
        this.serverUser = serverUser;
        this.anonymous = SystemGroupBackend.getGroup(ANONYMOUS_USERS);
        this.registered = SystemGroupBackend.getGroup(REGISTERED_USERS);
        this.owners = SystemGroupBackend.getGroup(PROJECT_OWNERS);
    }*/

    @Override
    public void run() throws Exception {
/*        AbstractModule mod = new AbstractModule() {
            @Override
            protected void configure() {
                bind(GitRepositoryManager.class).to(InMemoryRepositoryManager.class);
            }
        };

        injector = Guice.createInjector(mod);
        injector.injectMembers(this);*/
    }

    @Override
    public void postRun() throws Exception {
        AuthType authType =
                flags.cfg.getEnum(AuthType.values(), "auth", null, "type", null);
        if (authType != AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
            return;
        }
        System.out.println("Auth Type : " + authType);

        Config cfg = allProjectsConfig.load().getConfig();
        Set<String> sections = cfg.getSections();
        for (String section : sections) {
            System.out.println("Section : " + section);
            Set<String> sub = cfg.getSubsections(section);
            for (String key : sub) {
                System.out.println("Key : " + key + "of subsection : " + sub);
            }
        }

        ReviewDb db = dbFactory.open();


        Repository git = null;

        try {
            git = mgr.openRepository(allProjectsName);
            initAllProjects(git);
        } catch (RepositoryNotFoundException notFound) {
            // A repository may be missing if this project existed only to store
            // inheritable permissions. For example 'All-Projects'.
            try {
                git = mgr.createRepository(allProjectsName);
                initAllProjects(git);

                RefUpdate u = git.updateRef(Constants.HEAD);
                u.link(RefNames.REFS_CONFIG);
            } catch (RepositoryNotFoundException err) {
                String name = allProjectsName.get();
                throw new IOException("Cannot create repository " + name, err);
            }
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    private String readEmail(AccountSshKey sshKey) {
        String defaultEmail = "admin@gmail.com";
        if (sshKey != null && sshKey.getComment() != null) {
            String c = sshKey.getComment().trim();
            if (EmailValidator.getInstance().isValid(c)) {
                defaultEmail = c;
            }
        }
        return readEmail(defaultEmail);
    }

    private String readEmail(String defaultEmail) {
        String email = ui.readString(defaultEmail, "email");
        if (email != null && !EmailValidator.getInstance().isValid(email)) {
            ui.message("error: invalid email address\n");
            return readEmail(defaultEmail);
        }
        return email;
    }

    private AccountSshKey readSshKey(Account.Id id) throws IOException {
        String defaultPublicSshKeyFile = "";
        Path defaultPublicSshKeyPath =
                Paths.get(System.getProperty("user.home"), ".ssh", "id_rsa.pub");
        if (Files.exists(defaultPublicSshKeyPath)) {
            System.out.println("SSH Key exist");
            defaultPublicSshKeyFile = defaultPublicSshKeyPath.toString();
        }
        String publicSshKeyFile =
                ui.readString(defaultPublicSshKeyFile, "public SSH key file");
        return !Strings.isNullOrEmpty(publicSshKeyFile)
                ? createSshKey(id, publicSshKeyFile) : null;
    }

    private AccountSshKey createSshKey(Account.Id id, String keyFile)
            throws IOException {
        Path p = Paths.get(keyFile);
        if (!Files.exists(p)) {
            throw new IOException(String.format(
                    "Cannot add public SSH key: %s is not a file", keyFile));
        }
        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        return new AccountSshKey(new AccountSshKey.Id(id, 0), content);
    }

    /**
     * Create a new exception to indicate we won't continue.
     */
    protected static Die die(String why) {
        return new Die(why);
    }

    /**
     * Create a new exception to indicate we won't continue.
     */
    protected static Die die(String why, Throwable cause) {
        return new Die(why, cause);
    }


    private void initAllProjects(Repository git)
            throws IOException, ConfigInvalidException {
        MetaDataUpdate md = new MetaDataUpdate(
                GitReferenceUpdated.DISABLED,
                allProjectsName,
                git);
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);
        md.setMessage(MoreObjects.firstNonNull(
                Strings.emptyToNull(message),
                "Initialized Gerrit Code Review " + Version.getVersion()));

        ProjectConfig config = ProjectConfig.read(md);
        Project p = config.getProject();
        p.setDescription("Access inherited by all other projects.");
        p.setRequireChangeID(InheritableBoolean.TRUE);
        p.setUseContentMerge(InheritableBoolean.TRUE);
        p.setUseContributorAgreements(InheritableBoolean.FALSE);
        p.setUseSignedOffBy(InheritableBoolean.FALSE);

        AccessSection cap = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
        AccessSection all = config.getAccessSection(AccessSection.ALL, true);
        AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);
        AccessSection tags = config.getAccessSection("refs/tags/*", true);
        AccessSection meta = config.getAccessSection(RefNames.REFS_CONFIG, true);
        AccessSection magic = config.getAccessSection("refs/for/" + AccessSection.ALL, true);

        grant(config, cap, GlobalCapability.ADMINISTRATE_SERVER, admin);
        grant(config, all, Permission.READ, admin, anonymous);

        if (batch != null) {
            Permission priority = cap.getPermission(GlobalCapability.PRIORITY, true);
            PermissionRule r = rule(config, batch);
            r.setAction(PermissionRule.Action.BATCH);
            priority.add(r);

            Permission stream = cap.getPermission(GlobalCapability.STREAM_EVENTS, true);
            stream.add(rule(config, batch));
        }

        LabelType cr = initCodeReviewLabel(config);
        grant(config, heads, cr, -1, 1, registered);
        grant(config, heads, cr, -2, 2, admin, owners);
        grant(config, heads, cr, -2, 2, batch);
        grant(config, heads, Permission.CREATE, admin, owners);
        grant(config, heads, Permission.PUSH, admin, owners);
        grant(config, heads, Permission.SUBMIT, admin, owners);
        grant(config, heads, Permission.FORGE_AUTHOR, registered);
        grant(config, heads, Permission.FORGE_COMMITTER, admin, owners);
        grant(config, heads, Permission.EDIT_TOPIC_NAME, true, admin, owners);

        grant(config, tags, Permission.PUSH_TAG, admin, owners);
        grant(config, tags, Permission.PUSH_SIGNED_TAG, admin, owners);

        grant(config, magic, Permission.PUSH, registered);
        grant(config, magic, Permission.PUSH_MERGE, registered);

        meta.getPermission(Permission.READ, true).setExclusiveGroup(true);
        grant(config, meta, Permission.READ, admin, owners);
        grant(config, meta, cr, -2, 2, admin, owners);
        grant(config, meta, Permission.PUSH, admin, owners);
        grant(config, meta, Permission.SUBMIT, admin, owners);

        config.commit(md);
        // projectCache.evict(p);
        // config.commitToNewRef(md, RefNames.REFS_CONFIG);
    }

    public static LabelType initCodeReviewLabel(ProjectConfig c) {
        LabelType type = new LabelType("Code-Review", ImmutableList.of(
                new LabelValue((short) 2, "Looks good to me, approved"),
                new LabelValue((short) 1, "Looks good to me, but someone else must approve"),
                new LabelValue((short) 0, "No score"),
                new LabelValue((short) -1, "I would prefer this is not merged as is"),
                new LabelValue((short) -2, "This shall not be merged")));
        type.setCopyMinScore(true);
        type.setCopyAllScoresOnTrivialRebase(true);
        c.getLabelSections().put(type.getName(), type);
        return type;
    }


/*    private AccountGroup newGroup(ReviewDb c, String name, AccountGroup.UUID uuid)
            throws OrmException {
        if (uuid == null) {
            uuid = GroupUUID.make(name, serverUser);
        }
        return new AccountGroup( //
                new AccountGroup.NameKey(name), //
                new AccountGroup.Id(c.nextAccountGroupId()), //
                uuid);
    }*/


}

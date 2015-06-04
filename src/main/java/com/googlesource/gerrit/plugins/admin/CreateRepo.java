package com.googlesource.gerrit.plugins.admin;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.*;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.pgm.init.api.AllProjectsConfig;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.reviewdb.client.*;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.commons.validator.routines.EmailValidator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

public class CreateRepo implements InitStep {

    private final ConsoleUI ui;
    private final InitFlags flags;
    private final String pluginName;
    private final AllProjectsConfig allProjectsConfig;
    private SchemaFactory<ReviewDb> dbFactory;
    private final GitRepositoryManager mgr;
    private final AllProjectsName allProjectsName;
    private final PersonIdent serverUser;
    private String message;

    private GroupReference admin;
    private GroupReference batch;
    private GroupReference anonymous;
    private GroupReference registered;
    private GroupReference owners;

    @Inject
    CreateRepo(@PluginName String pluginName, ConsoleUI ui,
                  AllProjectsConfig allProjectsConfig, InitFlags flags,
                  GitRepositoryManager mgr, AllProjectsName allProjectsName,
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
    }

    @Inject(optional = true)
    void set(SchemaFactory<ReviewDb> dbFactory) {
        this.dbFactory = dbFactory;
    }


    public CreateRepo setAdministrators(GroupReference admin) {
        this.admin = admin;
        return this;
    }

    public CreateRepo setBatchUsers(GroupReference batch) {
        this.batch = batch;
        return this;
    }

    public CreateRepo setCommitMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public void run() throws Exception {
    }

    @Override
    public void postRun() throws Exception {
        AuthType authType =
                flags.cfg.getEnum(AuthType.values(), "auth", null, "type", null);
        if (authType != AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
            return;
        }
        System.out.println("Auth Type : " + authType);

        ReviewDb db = dbFactory.open();


        // Create git repo
        create();

/*        Repository repo = mgr.createRepository(allProjectsName);
        ProjectConfig allProjects = new ProjectConfig(new Project.NameKey(allProjectsName.get()));
        allProjects.load(repo);
        Map<String, LabelType> labels = allProjects.getLabelSections();
        for (Object key : labels.keySet()) {
            System.out.println("Label : " + key.toString() + " Value : " + labels.get(key));
        }*/

        try {
            if (db.accounts().anyAccounts().toList().isEmpty()) {
                ui.header("Gerrit Administrator");
                System.out.println("Create administrator user");
                Account.Id id = new Account.Id(db.nextAccountId());
                String username = ui.readString("admin", "username");
                String name = ui.readString("Administrator", "name");
                String httpPassword = ui.readString("secret", "HTTP password");
                AccountSshKey sshKey = readSshKey(id);
                String email = readEmail(sshKey);
                AccountExternalId extUser =
                        new AccountExternalId(id, new AccountExternalId.Key(
                                AccountExternalId.SCHEME_USERNAME, username));
                if (!Strings.isNullOrEmpty(httpPassword)) {
                    extUser.setPassword(httpPassword);
                }
                db.accountExternalIds().insert(Collections.singleton(extUser));

                if (email != null) {
                    AccountExternalId extMailto =
                            new AccountExternalId(id, new AccountExternalId.Key(
                                    AccountExternalId.SCHEME_MAILTO, email));
                    extMailto.setEmailAddress(email);
                    db.accountExternalIds().insert(Collections.singleton(extMailto));
                }

                Account a = new Account(id, TimeUtil.nowTs());
                a.setFullName(name);
                a.setPreferredEmail(email);
                db.accounts().insert(Collections.singleton(a));

                AccountGroupMember m =
                        new AccountGroupMember(new AccountGroupMember.Key(id,
                                new AccountGroup.Id(1)));
                db.accountGroupMembers().insert(Collections.singleton(m));

                if (sshKey != null) {
                    db.accountSshKeys().insert(Collections.singleton(sshKey));
                }
            } else {
                String env_user = System.getenv("GERRIT_ADMIN_USER");
                String env_email = System.getenv("GERRIT_ADMIN_EMAIL");
                String env_fullname = System.getenv("GERRIT_ADMIN_FULLNAME");
                String env_pwd = System.getenv("GERRIT_ADMIN_PWD");

                System.out.println("Admin account already exist.");
                Account.Id id = Account.Id.parse("1000000");

                Account adm = db.accounts().get(id);
                adm.setUserName((env_user == null) ? "admin" : env_user);
                adm.setPreferredEmail((env_email == null) ? "admin@fabric8.io" : env_email);
                adm.setFullName((env_fullname == null) ? "Administrator" : env_fullname);
                db.accounts().update(Collections.singleton(adm));

                AccountExternalId.Key extId_key = new AccountExternalId.Key( AccountExternalId.SCHEME_USERNAME, adm.getUserName() );
                AccountExternalId extUser = db.accountExternalIds().get(extId_key);
                if (extUser != null) {
                    extUser.setPassword((env_pwd == null) ? "secret" : env_pwd);
                    System.out.println("Set admin password : " + extUser.getPassword());
                    db.accountExternalIds().update(Collections.singleton(extUser));
                }

                AccountSshKey sshKey = readSshKey(id);

                System.out.println("Full Name :"  + adm.getFullName());
                System.out.println("User Name :" + adm.getUserName());
                System.out.println("Email :" + adm.getPreferredEmail());

                if (sshKey != null) {
                    db.accountSshKeys().insert(Collections.singleton(sshKey));
                    System.out.println("SSH Key :" + sshKey.getSshPublicKey());
                }
            }
        } finally {
            db.close();
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

    public void create() throws IOException, ConfigInvalidException {
        Repository git = null;
        try {
            git = mgr.openRepository(allProjectsName);
            System.out.println("Git repo exists");
            initAllProjects(git);
        } catch (RepositoryNotFoundException notFound) {
            // A repository may be missing if this project existed only to store
            // inheritable permissions. For example 'All-Projects'.
            try {
                git = mgr.createRepository(allProjectsName);
                System.out.println("Git repo created");
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

        config.commitToNewRef(md, RefNames.REFS_CONFIG);
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

    public static void grant(ProjectConfig config, AccessSection section,
                             String permission, GroupReference... groupList) {
        grant(config, section, permission, false, groupList);
    }

    public static void grant(ProjectConfig config, AccessSection section,
                             String permission, boolean force, GroupReference... groupList) {
        Permission p = section.getPermission(permission, true);
        for (GroupReference group : groupList) {
            if (group != null) {
                PermissionRule r = rule(config, group);
                r.setForce(force);
                p.add(r);
            }
        }
    }

    public static void grant(ProjectConfig config,
                             AccessSection section, LabelType type,
                             int min, int max, GroupReference... groupList) {
        String name = Permission.LABEL + type.getName();
        Permission p = section.getPermission(name, true);
        for (GroupReference group : groupList) {
            if (group != null) {
                PermissionRule r = rule(config, group);
                r.setRange(min, max);
                p.add(r);
            }
        }
    }

    public static PermissionRule rule(ProjectConfig config, GroupReference group) {
        return new PermissionRule(config.resolve(group));
    }
}


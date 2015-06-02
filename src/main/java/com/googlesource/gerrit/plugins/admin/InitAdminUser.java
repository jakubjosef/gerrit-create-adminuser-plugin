package com.googlesource.gerrit.plugins.admin;


import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.AllProjectsConfig;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.reviewdb.client.*;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.commons.validator.routines.EmailValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class InitAdminUser implements InitStep {

    private final ConsoleUI ui;
    private final InitFlags flags;
    private final String pluginName;
    private final AllProjectsConfig allProjectsConfig;
    private SchemaFactory<ReviewDb> dbFactory;

    @Inject
    InitAdminUser(@PluginName String pluginName, ConsoleUI ui,
                  AllProjectsConfig allProjectsConfig, InitFlags flags) {
        this.pluginName = pluginName;
        this.allProjectsConfig = allProjectsConfig;
        this.flags = flags;
        this.ui = ui;
    }

    @Inject(optional = true)
    void set(SchemaFactory<ReviewDb> dbFactory) {
        this.dbFactory = dbFactory;
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

                System.out.println("Admin account already exist.");
                Account.Id id = Account.Id.parse("1000000");

                Account adm = db.accounts().get(id);
                adm.setUserName((env_user == null) ? "Admin" : env_user);
                adm.setPreferredEmail((env_email == null) ? "admin@fabric8.io" : env_email);
                adm.setFullName((env_fullname == null) ? "Administrator" : env_fullname);
                db.accounts().update(Collections.singleton(adm));

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
}

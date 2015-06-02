# Instructions followed to test the plugin

- Download gerrit-war

```
curl -L -o target/gerrit.war https://gerrit-releases.storage.googleapis.com/gerrit-2.11.war
```

- Generate a new gerrit site, copy our gerrit.config where the authentication type is defined to "DEVELOPMENT_BECOME_ACCOUNT" & the plugin generate to create a default admin user and import your ssh public key

```
./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site
export GERRIT_ADMIN_USER='admin1'
export GERRIT_ADMIN_FULLNAME='Administrator1'
export GERRIT_ADMIN_EMAIL='admin1@fabric8.io'
java -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site
cp target/create-user-plugin-1.0-SNAPSHOT.jar target/gerrit-site/plugins/
cp config/gerrit.config target/gerrit-site/etc
java -jar target/gerrit.war init --batch -d target/gerrit-site
./target/gerrit-site/bin/gerrit.sh start
```

- Open the web browser at the address `http://localhost:8080/login/%23%2F` and check if the admin user exists

![Admin User](admin_user.png)

- Commands used to start/stop, check status

```
./target/gerrit-site/bin/gerrit.sh start

./target/gerrit-site/bin/gerrit.sh stop
./target/gerrit-site/bin/gerrit.sh status
```

- Consult database records

```
./target/gerrit-site/bin/gerrit.sh stop
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SHOW TABLES'
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SELECT * FROM ACCOUNTS'
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SELECT * FROM ACCOUNT_EXTERNAL_IDS'
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SELECT * FROM ACCOUNT_GROUPS'
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SELECT * FROM ACCOUNT_GROUP_MEMBERS'
java -jar target/gerrit.war gsql -d target/gerrit-site -c 'SELECT * FROM ACCOUNT_GROUP_NAMES'

```

- Test ssh connection

```
ssh -p 29418 admin@localhost

The authenticity of host '[localhost]:29418 ([::1]:29418)' can't be established.
RSA key fingerprint is da:46:52:0c:69:68:76:f8:32:16:81:47:17:6d:70:69.
Are you sure you want to continue connecting (yes/no)? yes
Warning: Permanently added '[localhost]:29418' (RSA) to the list of known hosts.

  ****    Welcome to Gerrit Code Review    ****

  Hi Administrator, you have successfully connected over SSH.

  Unfortunately, interactive shells are disabled.
  To clone a hosted Git repository, use:

  git clone ssh://admin@192.168.1.3:29418/REPOSITORY_NAME.git

Connection to localhost closed.
```

- Run some commands

```
List members

ssh -p 29418 admin@localhost gerrit ls-members Administrators
id      username        full name       email
1000000 admin   Administrator   ch007m@gmail.com

Lis tof plugins

ssh -p 29418 admin@localhost gerrit plugin ls -a --format json
{
  "create-admin-user": {
    "id": "create-admin-user",
    "version": "1.0-SNAPSHOT",
    "index_url": "plugins/create-admin-user/"
  }
}
```

         
      

        


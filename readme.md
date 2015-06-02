# Instructions followed to test the plugin

- Download gerrit-war

```
curl -L -o target/gerrit.war https://gerrit-releases.storage.googleapis.com/gerrit-2.11.war
```

- Generate a new gerrit site, copy our gerrit.config where the authentication type is defined to "DEVELOPMENT_BECOME_ACCOUNT" & the plugin generate to create a default admin user and import your ssh public key

```
./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site
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
```

         
      

        


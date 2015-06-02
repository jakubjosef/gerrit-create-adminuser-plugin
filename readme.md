# Instructions followed to test the plugin

- Download gerrit-war

```
curl -L -o target/gerrit.war https://gerrit-releases.storage.googleapis.com/gerrit-2.11.war
```

- Generate a new gerrit site, cp our gerrit.config & the plugin genrate to create a user

```
rm -rf target/gerrit-site
java -jar gerrit.war init --batch --no-auto-start -d target/gerrit-site
cp target/create-user-plugin-1.0-SNAPSHOT.jar target/gerrit-site/plugins/
cp config/gerrit.config target/gerrit-site/etc
```

- open the web browser at the address `http://localhost:8080/login/%23%2F` and check if the admin user exists

user-not-there.png

- Commands used to start/stop & check status 

```
./target/gerrit-site/bin/gerrit.sh start

./target/gerrit-site/bin/gerrit.sh stop
./target/gerrit-site/bin/gerrit.sh status`
```
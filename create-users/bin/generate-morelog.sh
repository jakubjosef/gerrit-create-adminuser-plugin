#!/usr/bin/env bash

./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site

export GERRIT_ADMIN_USER='admin'
export GERRIT_ADMIN_FULLNAME='Administrator'
export GERRIT_ADMIN_EMAIL='admin1@fabric8.io'
export GERRIT_ADMIN_PWD='mysecret'
export GERRIT_ACCOUNTS='jenkins,jenkins,jenkins@fabric8.io,secret,Non-Interactive Users:Administrators;sonar,sonar,sonar@fabric8.io,secret,Non-Interactive Users'
export GERRIT_PUBLIC_KEYS_PATH='/Users/chmoulli/MyProjects/gerrit-plugins/create-users/ssh-keys'

echo ">> Generate gerrit site folders"
java -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site

cp target/add-user-plugin-2.11.3-SNAPSHOT.jar target/gerrit-site/plugins/
cp config/gerrit.config target/gerrit-site/etc

echo ">> Execute plugins added"
java -Dlog4j.configuration=file:///Users/chmoulli/MyProjects/gerrit-plugins/create-users/config/log4j.properties -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site

echo ">> Launch Gerrit"
java -Dlog4j.configuration=file:///Users/chmoulli/MyProjects/gerrit-plugins/create-users/config/log4j.properties -jar target/gerrit.war daemon -d target/gerrit-site
#java -jar target/gerrit.war daemon -d target/gerrit-site


#!/usr/bin/env bash
./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site
export GERRIT_ADMIN_USER='admin'
export GERRIT_ADMIN_FULLNAME='Administrator'
export GERRIT_ADMIN_EMAIL='admin1@fabric8.io'
export GERRIT_ADMIN_PWD='mysecret'
export GERRIT_ACCOUNTS='jenkins,jenkins,jenkins@fabric8.io,secret,Non-Interactive Users:Administrators;sonar,sonar,sonar@fabric8.io,secret,Non-Interactive Users'
export GERRIT_PUBLIC_KEYS_PATH='/Users/ceposta/fabric8/gerrit-create-adminuser-plugin/create-users/ssh-keys'

# delete these
#export GERRIT_GIT_LOCALPATH='/Users/ceposta/fabric8/gerrit-create-adminuser-plugin/create-users/target/git'
#export GERRIT_GIT_REMOTEPATH='ssh://admin@localhost:29418/All-Projects'
#export GERRIT_GIT_PROJECT_CONFIG='/Users/ceposta/fabric8/gerrit-create-adminuser-plugin/change-project-config/config/project.config'
#export GERRIT_ADMIN_PRIVATE_KEY='/Users/ceposta/fabric8/gerrit-create-adminuser-plugin/create-users/ssh-keys/id_admin_rsa'

java -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site

cp target/add-user-plugin-2.11.2.jar target/gerrit-site/plugins/
cp config/gerrit.config target/gerrit-site/etc

java -jar target/gerrit.war init --batch -d target/gerrit-site
./target/gerrit-site/bin/gerrit.sh start
java -jar ../change-project-config/target/change-project-config-1.0.jar

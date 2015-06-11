#!/usr/bin/env bash
./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site
export GERRIT_ADMIN_USER='admin'
export GERRIT_ADMIN_FULLNAME='Administrator'
export GERRIT_ADMIN_EMAIL='admin@example.com'
export GERRIT_ADMIN_PWD='mysecret'
java -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site
cp target/change-project-config-2.11.jar target/gerrit-site/plugins/
cp /Users/chmoulli/.m2/repository/com/googlesource/gerrit/plugins/add-user-plugin/2.11/add-user-plugin-2.11.jar target/gerrit-site/plugins/
cp config/gerrit.config target/gerrit-site/etc
java -Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y -jar target/gerrit.war init --batch -d target/gerrit-site

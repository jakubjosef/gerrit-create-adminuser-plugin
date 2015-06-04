#!/usr/bin/env bash
./target/gerrit-site/bin/gerrit.sh stop
rm -rf target/gerrit-site

java -Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y -jar target/gerrit.war init --batch --no-auto-start -d target/gerrit-site

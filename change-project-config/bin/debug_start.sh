#!/usr/bin/env bash

java  -Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n -jar target/gerrit.war daemon --console-log --show-stack-trace -d target/gerrit-site

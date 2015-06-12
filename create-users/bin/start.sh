#!/usr/bin/env bash

java -jar target/gerrit.war daemon --console-log --show-stack-trace -d target/gerrit-site

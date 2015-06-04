rm -rf ./target/mytestgit
mkdir -p ./target/mytestgit
cd ./target/mytestgit
git init
git config user.name "Administrator"
git config user.email "admin@example.com"
git remote add origin "ssh://admin@localhost:29418/All-Projects"
git fetch origin refs/meta/config:refs/remotes/origin/meta/config
git checkout meta/config
cp ../../config/project.config .
git commit -m "REPLACE OLD WITH NEW FILE" -a
git push origin meta/config:meta/config
cd ../..
##### GOOD ####
# dabou:~/Temp/createuserplugin/target/mytestgit$ more .git/config
# [core]
#         repositoryformatversion = 0
#         filemode = true
#         bare = false
#         logallrefupdates = true
#         ignorecase = true
#         precomposeunicode = true
# [user]
#         name = Administrator
#         email = admin@example.com
# [remote "origin"]
#         url = ssh://admin@localhost:29418/All-Projects
#         fetch = +refs/heads/*:refs/remotes/origin/*
# [branch "meta/config"]
#         remote = origin
#         merge = refs/heads/meta/config
#############
###### BAD #######
# [core]
#         symlinks = false
#         repositoryformatversion = 0
#         filemode = true
#         logallrefupdates = true
#         precomposeunicode = true
# [user]
#         name = Administrator
#         email = admin@example.com
# [remote "origin"]
#         url = ssh://admin@localhost:29418/All-Projects
#         fetch = +refs/heads/*:refs/remotes/origin/*




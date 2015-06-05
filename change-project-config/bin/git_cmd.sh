cd target
rm -rf mytestgit
mkdir -p mytestgit
cd mytestgit
git init
git config user.name "Administrator"
git config user.email "admin@example.com"
git remote add origin "ssh://admin@localhost:29418/All-Projects"
git fetch origin refs/meta/config:refs/remotes/origin/meta/config
git checkout meta/config
gitlog
cp ../../config/project.config .
git commit -m "REPLACE OLD WITH NEW FILE" -a
git push origin meta/config:meta/config
gitlog
git status
cd ../..



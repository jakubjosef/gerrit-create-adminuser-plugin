rm -rf ./target/mysite
mkdir -p ./target/mysite
cd ./target/mysite
git init
git config user.name "Administrator"
git config user.email "admin@example.com"
git remote add origin "ssh://admin@localhost:29418/All-Projects"
git fetch origin refs/meta/config:refs/remotes/origin/meta/config
git checkout meta/config
cp ../../config/project.config .
git commit -m "REPLACE OLD WITH NEW FILE 3" -a
git push origin meta/config:meta/config
cd ../..


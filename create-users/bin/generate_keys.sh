#!/usr/bin/env bash

declare -a arr=("admin" "jenkins" "sonar")
CURRENT_DIR=$PWD
SSH_DIR=$CURRENT_DIR/ssh-keys

echo "SSH KEYS DIR : $SSH_DIR"
for i in "${arr[@]}"

do
   echo "$i"
   f='id_'$i'_rsa'
   echo "File : $SSH_DIR/$f"
   ssh-keygen -b 4096 -t rsa -f $SSH_DIR/$f -q -N "" -C "$i@fabric8.io"
done
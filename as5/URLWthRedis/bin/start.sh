#!/bin/bash

mongod --fork --logpath /var/log/mongodb.log

redis-server --daemonize yes

java -jar /app/target/*.jar
#!/bin/bash

# Start free transactor
cd /opt/datomic-free-*
pkill -f datomic-transactor-free
./bin/transactor ./config/samples/free-transactor-template.properties &

# Start datomic console if it exists
if [[ $(ls /opt | grep datomic-pro | wc -l) -ne 0 ]]; then
    cd /opt/datomic-pro-*
    pkill -f datomic.console
    ./bin/console -p 8080 free datomic:free://localhost:4334/ &
fi

# Wait for the above ouput
sleep 8
#!/bin/bash


mvn  -Dhttps.protocols=TLSv1.2 install

cp target/player-poc-jar-with-dependencies.jar ~/Public


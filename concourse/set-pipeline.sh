#!/bin/bash
fly -t tools set-pipeline --load-vars-from ${HOME}/cft.credentials.yml -p cft -c pipeline.yml

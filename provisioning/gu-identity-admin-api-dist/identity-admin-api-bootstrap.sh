#!/bin/bash

source set-env.sh

adduser --home /$apptag --disabled-password --gecos \"\" $apptag

aws s3 cp s3://gu-$apptag-dist/$stacktag/$stagetag/$apptag/app.zip /$apptag/$apptag.zip
aws s3 cp s3://gu-$apptag-private/$stagetag/$apptag.conf /etc/gu/$apptag.conf


unzip /$apptag/$apptag.zip -d /$apptag
cp /$apptag/$apptag-1.0-SNAPSHOT/deploy/$apptag-upstart.conf /etc/init/$apptag.conf

chown -R $apptag /$apptag
sed -i "s/<APP>/$apptag/g" /etc/init/$apptag.conf
sed -i "s/<STAGE>/$stagetag/g" /etc/init/$apptag.conf

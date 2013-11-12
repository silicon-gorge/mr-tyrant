/bin/echo "postinstall script started [$1]"

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add tyranitar
else
  /sbin/chkconfig --list tyranitar
fi

mkdir -p /var/log/tyranitar

chown -R tyranitar:tyranitar /var/log/tyranitar

ln -s /var/log/tyranitar /usr/local/tyranitar/log

chown tyranitar:tyranitar /usr/local/tyranitar

/bin/echo "postinstall script finished"
exit 0

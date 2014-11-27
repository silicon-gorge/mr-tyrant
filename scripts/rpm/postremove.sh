/bin/echo "postremove script started [$1]"

if [ "$1" = 0 ]
then
  /usr/sbin/userdel -r tyrant 2> /dev/null || :
  /bin/rm -rf /usr/local/tyrant
fi

/bin/echo "postremove script finished"
exit 0

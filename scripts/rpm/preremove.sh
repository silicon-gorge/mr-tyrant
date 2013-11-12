/bin/echo "preremove script started [$1]"

prefixDir=/usr/local/tyranitar
identifier=tyranitar.jar

isJettyRunning=`pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l`
if [ $isJettyRunning -eq 0 ]
then
  /bin/echo "Jetty is not running"
else
  sleepCounter=0
  sleepIncrement=2
  waitTimeOut=600
  /bin/echo "Timeout is $waitTimeOut seconds"
  /bin/echo "Jetty is running, stopping service"
  /sbin/service tyranitar stop &
  myPid=$!
  
  until [ `pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l` -eq 0 ]  
  do
    if [ $sleepCounter -ge $waitTimeOut ]
    then
      /usr/bin/pkill -KILL -f '$identifier'
      /bin/echo "Killed Jetty"
      break
    fi
    sleep $sleepIncrement
    sleepCounter=$(($sleepCounter + $sleepIncrement))
  done

  wait $myPid

  /bin/echo "Jetty down"
fi

if [ "$1" = 0 ]
then
  /sbin/chkconfig --del tyranitar
else
  /sbin/chkconfig --list tyranitar
fi

/bin/echo "preremove script finished"
exit 0

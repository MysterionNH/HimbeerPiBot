### BEGIN INIT INFO
# Provides: twitterCommands
# Required-Start: $all
# Required-Stop: $all
# Default-Start: 2 3 4 5
# Default-Stop: 0 1 6
# Short-Description: Allows my (Java based) TwitterBot esay access to certain bash commands
### END INIT INFO
#!/bin/sh
case "$1" in
        # reboot
        reboot)
            echo `sudo reboot`;
            ;;
        # exec {command to be executed, with args}
        exec)
            echo `sudo ${@:2}`;
            ;;
        # mail {@name} {mailAddress} {mailSubject} {mailBody}
        mail)
            echo `echo "$5"` "\n\n\nMail issued by @$2" -e | mail -s "$4" "$3";
            ;;
        # mailFile {@name} {pathToFile} {mailSubject} {mailAddress}
        mailFile)
            echo $(echo) "Hey, here is the file requested by @$2 at \"$3\"." | mail -A "$3" -s "$4" "$5";
            ;;
        # DO NOT USE WITH TOO LARGE BACKUPS
        # mailBackup {@name} {pathToDirectory} {backupName/mailSubject} {mailAddress}
        mailBackup)
            tar -cvzf /tmp/"$4".tar.gz "$3";
            echo $(echo) "Hey, here is the backup of \"$3\" requested by @$2." | mail -A /tmp/"$4".tar.gz -s "$4" "$5";
            rm /tmp/"$3".tar.gz;
            ;;
        *)
            echo "Unknown command";
            ;;
esac

exit 1;

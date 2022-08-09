### XDAGJ Configuration Time Synchronization

------

#### Linux（Ubuntu）

##### 1.Install ntp

```
sudo apt install ntp ntpdate
```

##### 2. Stop the npt service and cancel the startup

```
systemctl stop ntp
sudo systemctl disable ntp
```

##### 3. Enable scheduled task synchronization

```
sudo crontab -e
```

Enter the following statement and save:

```
*/5 * * * * /usr/sbin/ntpdate time.nist.gov 			
```

##### 4. Write the system time to the BIOS

```
sudo hwclock -w 
```

##### 5. Verify

Modify the local time, and automatically synchronize the correct time after five minutes.


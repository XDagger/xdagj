### XDAGJ Configuration Time Synchronization

------

#### Linux（Ubuntu）：Operate with root privileges

##### 1.Install ntp

```
apt install ntp ntpdate
```

##### 2. Modify the configuration file（etc/ntp.conf）

```
echo server time.nist.gov perfer iburst>>/etc/ntp.conf 
```

##### 3. Set up firewall

```
firewall-cmd --zone=public --add-port=123/udp --permanent 
firewall-cmd --reload  
```

##### 4. Enable ntp service and set ntp to start automatically

```
systemctl restart ntp    					
systemctl enable ntp    			
```

##### 5. Write the system time to the BIOS

```
hwclock -w 
```

##### 6. Verify

Modify the local time, and automatically synchronize the correct time after a few minutes.


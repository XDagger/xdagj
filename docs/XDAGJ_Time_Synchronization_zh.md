### XDAGJ 配置时间同步

------

#### Linux（Ubuntu）：在root权限下操作

##### 1.下载 ntp

```
apt install ntp ntpdate
```

##### 2. 修改配置文件（etc/ntp.conf）

```
echo server time.nist.gov perfer iburst>>/etc/ntp.conf 
```

##### 3. 设置防火墙

```
firewall-cmd --zone=public --add-port=123/udp --permanent 
firewall-cmd --reload  
```

##### 4. 重启ntp服务并设置开机启动

```
systemctl restart ntp    					
systemctl enable ntp    			
```

##### 5. 将系统时间写入到主板

```
hwclock -w 
```

##### 6. 验证

修改本机时间，几分钟后会自动同步正确的时间。


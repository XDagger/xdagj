### XDAGJ 配置时间同步

------

#### Linux（Ubuntu）

##### 1.下载 ntp

```
sudo apt install ntp ntpdate
```

##### 2. 关闭ntp服务并取消开机启动

```
systemctl stop ntp
sudo systemctl disable ntp
```

##### 3. 开启定时任务同步

```
sudo crontab -e			
```

输入以下语句并保存：

```
*/5 * * * * /usr/sbin/ntpdate time.nist.gov 
```

##### 4. 将系统时间写入到主板

```
sudo hwclock -w 
```

##### 5. 验证

修改本机时间，5分钟后会自动同步正确的时间。

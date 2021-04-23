# Win10配置RandomX算法环境

## 开启本地安全组策略

改步骤仅家庭版用户需要操作，专业版默认打开本地安全组策略，用户无需操作

- 新建空白txt文档，输入以下内容后保存

```shell
@echo off
 
pushd "%~dp0"
 
dir /b C:\Windows\servicing\Packages\Microsoft-Windows-GroupPolicy-ClientExtensions-Package~3*.mum >List.txt
 
dir /b C:\Windows\servicing\Packages\Microsoft-Windows-GroupPolicy-ClientTools-Package~3*.mum >>List.txt
 
for /f %%i in ('findstr /i . List.txt 2^>nul') do dism /online /norestart /add-package:"C:\Windows\servicing\Packages\%%i"
 
pause
```

- 修改上述文件为xxx.bat文件，然后右键用管理员身份运行，等待程序运行结束即可

## 开启hugeoage功能

- 右键点击Win10电脑`开始`

- 找到`windows管理工具`，并在里面到`本地安全策略`后打开

- 点击`本地策略`->`用户权限分配`->`锁定内存页`

- 为锁定内存页添加计算机用户名，确认之后重启电脑

  （补充一些截图吧）
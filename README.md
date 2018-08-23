# UpdatePlugin
使用方法:
1.在自己的项目中使用ionic cordova plugin add cordova-plugin-push-master加载插件
2.在想要注册推送的.ts文件中添加以下操作：
① 在import和@Component添加 declare let cordova: any;
② 实现下面的方法


    // 自动更新
    cordova.plugins.UpdatePlugin.coolMethod("ios",function (message) {
    },
    function (error) {
    });
    
注意事项：
这个插件的自动跟新只适合ios的企业证书的ipa包。

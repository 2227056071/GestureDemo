# GestureDemo
简单的手势密码demo
## 一、实现原理
手势密码控件是在android源码LockPatternView的基础上修改的，源码绘制的轨迹是通过显示图片来实现的，本工程中是通过画笔绘制来实现的，不需要提供专门的切图。
### 优点：
可以自定义手势密码轨迹的颜色
### 缺点：
因低版本的android系统对绘图方法支持不到位，可能导致部分低端机在绘制手势密码轨迹时出现毛刺等
## 二、程序流程
在设置密码界面绘制手势密码成功后，可以在验证界面进行验证
## 三、效果图展示
### 设置手势密码
<img src="https://raw.githubusercontent.com/2227056071/imges/master/gestrue_draw.png" width = "300" height = "500" alt="图片名称"/>

### 验证手势密码
<img src="https://raw.githubusercontent.com/2227056071/imges/master/gesture_error.png" width = "300" height = "500" alt="图片名称" />

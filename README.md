# ArcfaceDemo
基于虹软android ArcfaceDemo(arcface 2.0）的扩展

相比于官网发布的arcface2.0 android demo:

**FIX BUG:**

1.修复在部分设备上画框偏移的问题

**功能修改:**

1.针对在部分设备上画框会有左右镜像或者上下镜像的情况，添加了上下镜像绘制和左右镜像绘制

2.针对在部分设备上相机打开自动旋转了90度的情况，CameraHelper.Builder添加additionalRotation（90的倍数）以自定义额外的相机旋转角度

3.CameraHelper可根据传入的控件宽高获取最佳相机比例（如果没有相同比例的，就选最接近的）

**效果展示：**
 ![正常预览](https://github.com/wangshengyang1996/ArcfaceDemo/blob/master/zxy1.jpg)
 ![圆角预览](https://github.com/wangshengyang1996/ArcfaceDemo/blob/master/zxy2.jpg)
 
**ftRect转化为view所需的Rect流程**

 ![ftRect转化为view所需的Rect流程](https://github.com/wangshengyang1996/ArcfaceDemo/blob/master/rectChangeStep.jpg)
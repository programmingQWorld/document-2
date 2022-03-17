[JVM虚拟机参数]

-Xverifynone
关闭大部分的类验证措施，以缩短虚拟机类加载的时间

-Xbootclasspath
启动类加载器加载指定路径下，且能被虚拟机识别的类库（如rt.jar，所有的java.开头的类均被Bootstrap ClassLoader加载）

-Xms
设置堆的最小空间大小。
-Xmx
设置堆的最大空间大小。
-XX
NewSize设置新生代最小空间大小。
-XX:MaxNewSize
设置新生代最大空间大小。
-XX:PermSize
设置永久代最小空间大小。
-XX:MaxPermSize
设置永久代最大空间大小。
-Xss
设置每个线程的堆栈大小。

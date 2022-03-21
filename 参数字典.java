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


-XX:+HeapDumpOnOutOfMemoryError
    '参数含义'：'当堆内存空间溢出时' '输出堆的内存快照。'
    配合参数：-XX:+'HeapDumpOnOutOfMemoryError' -XX:'HeapDumpPath'=/export/home/tomcat/logs/
    触发条件：java.lang.OutOfMemo-ryError: Java heap space. 也就是说'当发生OutOfMemoryError错误时'，才能触发-XX:HeapDumpOnOutOfMemoryError '输出到' -XX:HeapDumpPath'指定位置'。

-XX:HeapDumpPath
    参数表示'生成DUMP文件的路径'，'也可以指定文件名称'.
    例如：-XX:HeapDumpPath=${目录}/java_heapdump.hprof。如果不指定文件名，则默认名字为：java_<pid>_<date>_<time>_heapDump.hprof

-verbosegc参数 
    允许在每次GC过程开始时候生成跟踪。
    




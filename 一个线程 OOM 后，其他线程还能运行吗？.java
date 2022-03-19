一个线程 OOM 后，其他线程还能运行吗？

由于面试官仅提到OOM，但 Java 的OOM又分很多类型的呀：

'堆溢出'         'java.lang.OutOfMemoryError:Java heap space'
'永久代' '溢出'  'java.lang.OutOfMemoryError:Permgen space'
'不能创建线程'    'java.lang.OutOfMemoryError:Unable to create new native thread'

OOM在《Java虚拟机规范》里，除'程序计数器'，虚拟机内存的'其他几个运行时区域都可能发生OOM'，那'本文的目的是啥'呢？

通过'代码验证'《Java虚拟机规范》中描述的'各个运行时区域储存'的内容
在工作中'遇到'实际的'内存溢出异常'时，能'根据异常的提示信息' 迅速'得知是哪个区域的内存溢出'，
知道'怎样的代码' 可能会'导致这些区域内存溢出'，以及'出现这些异常后'该如何处理。
'本文代码'均由笔者在基于OpenJDK 8中的HotSpot虚拟机上'进行过实际测试'。

1 'Java堆溢出'
    Java堆用于储存对象实例，只要'不断地创建对象'，并且保证GC Roots到对象之间有可达路径来'避免GC机制清除这些对象'，
    则'随对象数量增加'，'总容量'触及'最大堆的容量限制'后就会'产生内存溢出异常'。
    '限制Java堆的大小20MB，不可扩展'
    -XX:+HeapDumpOnOutOf-MemoryError: 可以让虚拟机'在出现内存溢出异常的时候' 'Dump出当前的内存堆转储快照'。

    案例1
        public class Heap00m {
            static class 00MObject {}
            public static void main(String[] args) {
                List<0oMObject> list = new ArrayList<>(）；
                while(true) {
                    list.add(new 0oMobject());
                }
            }
        }

        报错 [是一张图片截图。https://pic4.zhimg.com/v2-280cff0d722e7a4ae694c2f46b0c7957_r.jpg]

        'Java堆内存的OOM'是实际应用中'最常见的内存溢出异常场景'。出现'Java堆内存溢出'时，
        '异常堆栈信息'“java.lang.OutOfMemoryError”会跟随 '进一步提示' "Java heap space"。

        那既然发生了，'如何解决这个内存区域的异常呢'？ 
            
            一般先通过内存映像分析工具（如'jprofile'）'对Dump'出来的'堆转储快照'进行'分析'。
            第一步,分清楚到底是'内存泄漏'（Memory Leak）,还是'内存溢出'（Memory Overflow）
            下图是使用 'jprofile打开的堆转储快照文件'（java_pid44526.hprof）

                ['类'] [分配] [最大对象] [引用] [时间] [检查] [图表]         
                    当前对象集：'236个类'的'816,146个对象'

            若是内存泄漏，可'查看' '泄漏对象到GC Roots的引用链'，找到泄漏对象是'通过怎样的引用路径'、与'哪些GC Roots相关联'，才导致垃圾收集器'无法回收它们'，
            根据'泄漏对象的类型信息'以及'它到GC Roots引用链'的信息，一般可以'比较准确地定位到'这些'对象创建的位置'，进而'找出产生内存泄漏的代码'的'具体位置'。

            若'不是内存泄漏'，即就是'内存中的对象'确实都'必须存活'，则应：

                '检查JVM堆参数'（-Xmx与-Xms）的设置，'与机器内存对比'，看'是否还有' '向上调整的空间'
                再检查代码是否存在'某些对象生命周期过长'、'持有状态时间过长'、'存储结构设计不合理'等'情况'，'尽量减少' '程序运行期的内存消耗'
                以上是处理Java堆内存问题的简略思路。

    案例 2

        JVM启动参数设置：

        '-Xms5m -Xmx10m -XX:+HeapDumpOnOutOfMemoryError'

        代码：
            public class JvmThread {
                public static void main(String[] args) throws InterruptedException {
                    Thread.sleep(15000);
                    new Thread(() -> {
                        List<byte[]> = new ArrayList<>();
                        while(true) {
                            System.out.println(new Date().tostring() + Thread.currentThread(） + "==")；
                            byte[] b = new byte[1024 * 1024 * 1];
                            list.add(b);
                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();

                    new Thread(() -> {
                        while(true) {
                            Thread.sleep(1000);
                            System.out.println(new Date().tostring() + Thread.currentThread(） + "==")；
                            try {
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    })

                }
            }

            运行结果；
                java. Lang. OutofMemoryError: 'Java heap space'
                Dumping heap to javapid71393.hprof
                Heap dump file created [8830396 bytes in 0.021 secs]

        JVM堆空间的变化
            查看图片[https://pic3.zhimg.com/80/v2-a973ff82fd07dc274850509454ed39ae_1440w.jpg]

            堆的使用大小，突然抖动！说明'当一个线程抛OOM后'，它'所占据的内存资源' '会全部被释放掉'，而'不会影响其他线程的正常运行'！ 
            所以一个'线程溢出后'，'进程里的其他线程' '还能照常运行'。 
            '发生OOM的线程'一般情况下'会死亡'，也就是会'被终结掉'，该线程持有的对象占用的heap都会被gc了，释放内存。
            因为发生OOM之前要进行gc，'就算其他线程能够正常工作，也会因为频繁gc产生较大的影响'。

2 '虚拟机栈/本地方法栈溢出'

    由于'HotSpot JVM'并'不区分' '虚拟机栈和本地方法栈'，因此HotSpot的-Xoss参数（设置本地方法栈的大小）虽然存在，但无任何效果，'栈容量'只能'由-Xss参数设定'。

    关于'虚拟机栈'和'本地方法栈'，《Java虚拟机规范》'描述如下异常'：

        若'线程请求的栈深度'大于'虚拟机所允许的最大深度'，将'抛出StackOverflowError异常'
        若'虚拟机的栈内存' '允许动态扩展'，当'扩展栈容量' '无法申请到足够的内存'时，将抛出 OutOfMemoryError异常

    《Java虚拟机规范》明确'允许JVM实现自行选择' '是否支持' '栈的动态扩展'，而'HotSpot虚拟机的选择'是'不支持扩展'，
    所以除非在'创建线程申请内存'时就'因无法获得足够内存'而'出现OOM'，
    '否则' '在线程运行时' 是不会因为'扩展而导致内存溢出的'，'只会因为' '栈容量无法容纳新的栈帧' 而 '导致StackOverflowError'。
    [lincq补充]'方法递归调用'而没有正常结束情况下，也会应为栈容量无法容纳新的栈帧而导致'StackOverFlowError'

    如何验证呢？'做俩实验'

        1. 方法递归调用，新的栈帧申请导致oom
        2. 定义大量局部变量，增大此方法帧中本地变量表的长度

    ['实验一'] 
        
        先在单线程操作，尝试下面两种行为是否能让HotSpot OOM：
        '使用-Xss减少栈内存容量'
        代码示例
            /**
            * -Xss128k(设置每个线程的堆栈大小 为128K)
            *
            * java 虚拟机栈OOM
            * 虚拟机栈理论上有2种异常：
            *   1.StackOverflowError,线程请求的栈深度大于虚拟机所允许的深度。
            *   2.OutOfMemoryError栈扩展时申请到不足够的内存。
            *   为了让JVM，更容易出现StackOverflowError
            *   -Xss128k(设置每个线程的堆栈大小 为128K)。
            *
            *
            *   JVM参数： -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError -Xss128k -XX:+PrintGC
            *
            */
            public class JavaVMStackSOF {


                private int stackLength=1;
                public void stackLeak(){
                    stackLength++;
                    stackLeak();
                }

                public static void main(String[] args) {
                    JavaVMStackSOF stackSOF =new JavaVMStackSOF();
                    stackSOF.stackLeak();
                }
            }


        运行结果
            Exception in thread "main" java.lang.StackOverflowError
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)
                at com.example.jvm.JavaVMStackSOF.stackLeak(JavaVMStackSOF.java:20)


            抛StackOverflowError异常，'异常出现时' 输出的'堆栈深度相应缩小'。(lincq:好像没有从里面看到栈深度在减小)

        '不同版本的Java虚拟机'和'不同的操作系统'，'栈容量最小值'可能'会有所限制'，
            这主要'取决于' '操作系统内存分页大小'。
            譬如上述方法中的参数-Xss160k可以正常用于62位macOS系统下的JDK 8，但若用于64位Windows系统下的JDK 11，则'会提示栈容量最小不能低于180K'，
            而在Linux下这个值则可能是228K，如果低于这个最小限制，HotSpot虚拟器启动时会给出如下提示：
            'The stack size specified is too small, Specify at' 



    [实验二]定义大量局部变量，增大此方法帧中本地变量表的长度

        代码示例
        /**
        * StackOverFlowError
        * -Xss: stack start  减少栈内存容量
        */
        public class JavaVMStackSOF2 {
            private int stackLength = 1;

            public void stackLeak() {
                long useused1, useused2, useused3, useused4, useused5, useused6, useused7, useused8, useused9,
                        useused10, useused12, useused13, useused14, useused15, useused16, useused17, useused18, useused19,
                        useused20, useused22, useused23, useused24, useused25, useused26, useused27, useused28, useused29,
                        useused30, useused32, useused33, useused34, useused35, useused36, useused37, useused38, useused39;
                stackLength++;
                'stackLeak();'
                useused1 = useused2 = useused3 = useused4 = useused5 = useused6 = useused7 = useused8 = useused9 =
                        useused10 = useused12 = useused13 = useused14 = useused15 = useused16 = useused17 = useused18 = useused19 =
                                useused20 = useused22 = useused23 = useused24 = useused25 = useused26 = useused27 = useused28 = useused29 =
                                        useused30 = useused32 = useused33 = useused34 = useused35 = useused36 = useused37 = useused38 = useused39 = 0;
            }

            public static void main(String[] args) {
                JavaVMStackSOF2 oom = new JavaVMStackSOF2();
                try {
                    oom.stackLeak();
                } catch (Throwable e) {
                    System.out.println("stack length :" + oom.stackLength);
                    throw e;
                }

            }
        }


        结果
            stack length:3475
                Exception in thread "'main" java. Lang.StackOverflowError
                at com.javaedge.sof.JavaVMStackSOF2.test(JavawwStacksoF2iava:16)


        所以无论是由于'栈帧太小'或'虚拟机栈容量太小'，'当新的栈帧内存无法分配时'， HotSpot 都'抛SOF'。
        可若在'允许动态扩展栈容量大小'的虚拟机上，相同代码则会导致不同情况。

        若测试时不限于单线程（前面是'单线程'），而是'不断新建线程'，在HotSpot上'也会产生OOM'。
        但这样'产生OOM和栈空间是否足够' '不存在直接的关系'，主要'取决于os本身内存使用状态'。
        甚至说这种情况下，'给每个线程的栈' '分配的内存越大'，反而'越容易产生OOM'。 

        不难理解，os分配给每个进程的内存有限制，比如32位Windows的'单个进程最大内存限制'为'2G'。
        HotSpot'提供参数'可以'控制'Java'堆和方法区'这两部分的'内存的最大值'，那剩余的堆栈内存即为：2G（os限制）'减去最大堆容量'，再'减去最大方法区容量'，
        由于程序计数器消耗内存很小，'可忽略'，若把直接内存和虚拟机进程本身耗费的内存也去掉，'剩下的内存'就'由虚拟机栈和本地方法栈' '来分配了'。
        因此为每个'线程分配到的栈内存越大'，可以'建立的线程数量越少'，'建立越多线程时'就'越容易把剩下的内存耗尽'


        [代码示例] 不断创建线程，'栈扩展' '无法申请到更多内存'时引起的OOM
            public class JavaVMStackOOM {
                private void dontStop() {
                    while (true) {
                    }
                }
                public void stackLeakByThread(){
                    while(true){
                        new Thread(this::dontStop).start();
                    }
                }

                public static void main(String[] args) {
                    JavaVMStackOOM oom = new JavaVMStackOOM();
                    try {
                        oom.stackLeakByThread();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
            
        [运行结果] Exception in thread "main" java.lang.'OutOfMemoryError': 'unable to create native thread'

        出现SOF时，会'有明确错误堆栈' '可供分析'，'相对容易定位问题'。

        '如果使用'HotSpot虚拟机'默认参数'，'栈深度'在大多数情况下'到达1000~2000没有问题'，（因为每个方法压入栈的'帧大小并不是一样的'）
        对于正常的方法调用（包括不能做尾递归优化的递归调用），这个'深度'应该'完全够用'。

        但如果是'建立过多线程' '导致的内存溢出'，在不能减少线程数量或者更换64位虚拟机的情况下，就只能通过'减少最大堆和减少栈' '容量' '换取更多的线程'。
        这种'通过“减少其他区域内存”手段' '解决内存溢出'的方式，'如果没有这方面处理经验，一般比较难以想到'。
        也是由于这种问题较为隐蔽，从 JDK 7起，以上提示信息中“unable to create native thread”后面，虚拟机会特别注明原因.

        possibly #define OS_NATIVE_THREAD_CREATION_FAILED_MSG 	
            'unable to create native thread': possibly out of memory or 'process/resource limits reached'


3 '方法区和运行时常量池溢出'

运行时'常量池是方法区的一部分'，所以这两个区域的溢出测试可以放到一起。

HotSpot从JDK 7开始逐步“去永久代”，在JDK 8中'完全使用元空间代替永久代'，那么'方法区使用“永久代”还是“元空间”来实现，对程序有何影响呢'。

'String::intern()是一个本地方法'：若字符串常量池中已经包含一个等于此String对象的字符串，则返回代表池中这个字符串的String对象的引用；否则，会将此String对象包含的字符串添加到常量池，并且返回此String对象的引用。

'在JDK6或之前'HotSpot虚拟机，'常量池'都是'分配在永久代'，可以通过如下两个参数："PermSize","MaxPermSize"限制永久代的大小，即可'间接限制'其中'常量池的容量'，

[代码实例] 常量池溢出测试
/**
 * @author hubing
 * 证明字符串常量池是放在堆里面的。
 */
public class RuntimeConstantPoo100M {

    public static void main(String[] args) {
        // 使用集合保持着常量池引用，避免Ful1 GC回收常量池行为）
        ArrayList<String> list = new ArrayList<>();
        short i = 0;
        while(true) {
            // 在short范围内足以让6MB的PermSize产生00M了
            list.add(String.valueOf(i ++)'.intern()');
        }
    }
}

结果
Exception in thread "main" java.lang.'OutOfMemoryError': 'PermGen space '
	at java.lang.String.intern(Native Method) 
	at org.fenixsoft.oom.RuntimeConstantPoolOOM.main(RuntimeConstantPoolOOM.java: 18)


可见，运行时'常量池溢出'时，在OutOfMemoryError异常后面跟随的'提示信息是“PermGen space”'，说明运行时'常量池'的确'是属于方法区的一部分'。

而'使用JDK 7'或'更高版本的JDK'来'运行这段程序'并'不会得到相同的结果'，

'无论是'在JDK 7中继续使 用-XX：MaxPermSize参数,'或者在'JDK 8及以上版本使用-XX：MaxMeta-spaceSize参数'把方法区容量同样限制在6MB'，
也都不会重现JDK 6中的溢出异常，'循环将一直进行下去，永不停歇'。 
这种变化是因为自JDK 7起，原本存'放在永久代的字符串常量池'被'移至Java堆'，
所以在JDK 7及以上版 本，'限制方法区的容量'对 '该测试用例' 来说是'毫无意义'。

那怎么办呢，如何在Java7以上版本上测试呢？这时候'使用-Xmx参数' '限制最大堆 = 6MB'就能看到以下'两种运行结果'之一，具体取决于'哪里的对象分配时' '产生了溢出'：

OOM异常一： Exception in thread "main" java.lang.OutOfMemoryError: 'Java heap space '
    at java.base/java.lang.Integer.toString(Integer.java:440) 
    at java.base/java.lang.String.valueOf(String.java:3058) 
    at RuntimeConstantPoolOOM.main(RuntimeConstantPoolOOM.java:12) 

OOM异常二： Exception in thread "main" java.lang.OutOfMemoryError: 'Java heap space at java.base/java.util.HashMap.resize(HashMap.java:699) '
    at java.base/java.util.HashMap.putVal(HashMap.java:658) 
    at java.base/java.util.HashMap.put(HashMap.java:607) 
    at java.base/java.util.HashSet.add(HashSet.java:220) 
    at RuntimeConstantPoolOOM.main(RuntimeConstantPoolOOM.java from InputFile-Object:14)


字符串常量池的实现位置还有很多趣事：
    [代码示例]
    public class RuntimeConstantPoolOOM2 {
        public static void main(String[] args) {
            String str1 = new StringBuilder("计算机"）.append("软件").tostring();
            System. out.println(str1.intern() == str1)；
            String str2 = new StringBuilder("ja"）.append("va").tostring();
            System. out.println(str2.intern() == str2)；
        }
    }

    JDK 6中运行，结果是'两个false'
    JDK 7中运行，一个true和一个false


    因为JDK6的intern()会'把首次遇到的字符串实例' '复制到永久代的字符串常量池'中，返回的也是永久代里这个字符串实例的引用，
    而由'StringBuilder创建的字符串对象实例' '在 Java 堆'，所以不可能是同一个引用，结果将返回false。

    'JDK 7及以后的intern()' '无需再拷贝字符串的实例' 到永久代，'字符串常量池' '已移到Java堆'，只需'在常量池里记录'一下'首次出现的实例引用'，
    因此'intern()返回的引用'和'由StringBuilder创建的那个字符串实例' '是同一个'。

    str2比较返回false，这是因为'“java”这个字符串'在执行String-Builder.toString()之前就已经出现过了，
    '字符串常量池中已经有它的引用'，不符合intern()方法要求“首次遇到”的原则，而“计算机软件”这个字符串则是首次出现的，因此结果返回true！


对于'方法区的OOM测试'，'基本的思路'是'运行时产生大量类' '去填满方法区'，'直到溢出'。
虽然'直接使用Java SE API' '也可动态产生类'（如反射时的 GeneratedConstructorAccessor和动态代理），'但操作麻烦'。 
借助了'CGLib直接操作字节码' '运行时动态生成大量类'。 当前的很多主流框架，
如Spring、Hibernate对类进行增强时，都会使用到 CGLib字节码增强，

'当增强的类越多'，就'需要越大的方法区'以'保证动态生成的新类型可以载入内存'。 
很多'运行于JVM的动态语言'（例如Groovy）通常'都会持续创建新类型' '来支撑语言的动态性'，
随着这类动态语言的流行，与如下代码相似的溢出场景'也越来越容易遇到'

在JDK 7中的运行结果：

Caused by: java.lang.OutOfMemoryError: 'PermGen space '
	at java.lang.ClassLoader.defineClass1(Native Method) 
	at java.lang.ClassLoader.defineClassCond(ClassLoader.java:632) 
	at java.lang.ClassLoader.defineClass(ClassLoader.java:616)
JDK8及以后：可以使用
    -XX:'MetaspaceSize'=10M
    -XX:'MaxMetaspaceSize'=10M

设置'元空间初始大小'以及'最大可分配大小'。 
1.'如果不指定'元空间的大小，'默认情况'下，'元空间最大的大小'是'系统内存的大小'，元空间一直扩大，'虚拟机可能会消耗完' '所有的可用系统内存'。 
2.如果'元空间内存不够用'，就'会报OOM'。 
3.默认情况下，对应一个'64位的服务端JVM来说'，其默认的-XX:MetaspaceSize值为21MB，这就是'初始的高水位线'，一旦'元空间的大小触及这个高水位线'，就'会触发Full GC'并会'卸载没有用的类'，然后'高水位线的值'将'会被重置'。 
4.从第3点可以知道，如果'初始化的高水位线'设置'过低'，'会频繁的触发Full GC'，高水位线会被多次调整。所以'为了避免频繁GC'以及调整高水位线，建议将-XX:MetaspaceSize'设置为较高的值'，而-XX:MaxMetaspaceSize'不进行设置'。


'一个类如果要被gc'，要达成的条件比较苛刻。在经常'运行时生成大量动态类'的'场景'，就'需要特别关注' '这些类的回收状况'。
 这类场景除了之前提到的程序使用了CGLib字节码增强和动态语言外，常见的还有：

    '大量JSP'或动态产生JSP 文件的应用（JSP第一次运行时需要编译为Java类）
    '基于OSGi的应用'（即使是同一个类文件，'被不同的加载器加载'也会'视为不同的类'）

JDK8后，永久代完全废弃，而使用元空间作为其替代者。'在默认设置下'，前面列举的那些正常的'动态创建新类型的测试用例'已经'很难再迫使虚拟机产生方法区OOM'。 
为了让使用者有'预防' '实际应用里' '出现类似于如上代码那样的破坏性操作'，HotSpot还是'提供了一些参数' 作为 '元空间的防御措施'：

    -XX:MetaspaceSize 指定元空间的'初始空间大小'，'以字节为单位'，'达到该值'就'会触发垃圾收集'进行'类型卸载'，'同时'收集器会'对该值进行调整'。
    如果释放了大量的空间，就适当降低该值，如果释放了很少空间，则在不超过-XX:MaxMetaspaceSize（如果设置了的话）的情况下，适当提高该值

    -XX:MaxMetaspaceSize '设置元空间最大值'，'默认'-1，即'不限制'，或者说'只受限于本地内存的大小'
    -XX:MinMetaspaceFreeRatio 在GC后控制最小的元空间剩余容量的百分比，可'减小'因为元空间不足导致的'GC频率'
    -XX:Max-MetaspaceFreeRatio 控制最大的元空间剩余容量的百分比


'本机直接内存溢出'
    直接内存（Direct Memory）的容量大小可通过-XX:MaxDirectMemorySize指定，若不指定，则'默认与Java堆最大值'（-Xmx）一致。

    这里越过DirectByteBuffer类，直接'通过反射获取Unsafe实例'进行'内存分配'。 
    Unsafe类的getUnsafe()指定'只有引导类加载器'才会'返回实例'，体现了'设计者希望只有虚拟机标准类库里面的类才能使用Unsafe'，
    'JDK10时' '才将Unsafe的部分功能' 通过VarHandle开放给外部。 因为虽然使用DirectByteBuffer分配内存'也会抛OOM'，但'它抛异常时' '并未真正向os申请分配内存'，
    而是'通过计算得知内存无法分配'，就'在代码里手动抛了OOM'，真正申请分配内存的方法是'Unsafe::allocateMemory()'

    使用unsafe分配本机内存
        public class DirectMemoryOOM {
            private static final int SIZE = 1024 * 1024
            public static void main(String [〕 args) throws Exception {
                Field unsafeField = Unsafe.class.getDeclaredFields()[0];
                unsafeField. setAccessible(true);
                Unsafe unsafe = (Unsafe) unsafeField.get (null);
                while (true) {
                    unsafe.allocateMemory(SIZE);
                }
            }
        }
    结果

    由'直接内存导致的内存溢出'，一个'明显的特征'是'在Heap Dump文件中不会看见有什么明显异常'，
    若发现'内存溢出之后' '产生的Dump文件很小'，'而程序中又直接或间接使用了 DirectMemory'（比如使用'NIO'），则'该考虑直接内存'了。
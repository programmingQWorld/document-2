Jvm 系列(二):Jvm 内存结构

简介

所有的Java开发人员可能会遇到这样的困惑？
我该'为堆内存设置多大空间'呢？
'OutOfMemoryError的异常'到底'涉及到' '运行时数据的哪块区域'？'该怎么解决呢'？

其实如果你'经常解决服务器性能问题'，那么这些问题就会变的非常常见，
'了解JVM内存'也是为了服务器出现性能问题的时候'可以快速的了解那块的内存区域出现问题'，以便于'快速的解决生产故障'。

先看一张图，这张图能很清晰的说明'JVM内存结构布局'。

JVM内存结构主要有三大块：'堆内存'、'方法区'和'栈'。

    1.'堆内存'是JVM中最大的一块'由年轻代和老年代'组成，
    而'年轻代内存'又被分成'三部分'，'Eden空间'、'From Survivor空间'、'To Survivor空间',
    默认情况下年轻代按照'8:1:1的比例'来分配；

    2.'方法区'存储'类信息'、'常量'、'静态变量'等数据，'是线程共享的区域'，为与Java堆区分，
    方法区还有一个'别名Non-Heap'(非堆)；

    3.栈又分为'java虚拟机栈'和'本地方法栈'主要'用于方法的执行'。    

在通过一张图来了解'如何通过参数来控制各区域的内存大小'

    -Xms设置'堆的最小空间'大小。
    -Xmx设置'堆的最大空间'大小。
    -XX:NewSize设置'新生代最小空间'大小。
    -XX:MaxNewSize设置'新生代最大空间'大小。
    -XX:PermSize设置'永久代最小空间'大小。
    -XX:MaxPermSize设置'永久代最大空间'大小。
    -Xss设置'每个线程的堆栈大小'。

    没有直接'设置老年代的参数'，但是可以'设置堆空间大小'和'新生代空间大小'两个参数来间接控制。
    '老年代空间大小' = '堆空间大小-年轻代空间大小'

从'更高的一个维度'再次来看'JVM和系统调用之间的关系'

'方法区和堆'是'所有线程共享的内存区域'；而'java栈、本地方法栈和程序计数器'是'运行时线程私有的内存区域'。

下面我们'详细介绍每个区域的作用'

'Java堆（Heap）'
    
    对于'大多数应用来说'，'Java堆'（Java Heap）是'Java虚拟机所管理的内存中最大的一块'。
    'Java堆'是'被所有线程共享的一块内存区域'，'在虚拟机启动时'创建。'此内存区域的唯一目的'就是'存放对象实例'，
    几乎'所有的对象实例'都'在这里分配内存'。

    'Java堆'是'垃圾收集器管理'的主要区域，因此'很多时候也被称做“GC堆”'。如果'从内存回收的角度看'，由于现在收集器基本都是'采用的分代收集算法'，
    所以Java堆中还可以细分为：'新生代和老年代'；'新生代'还可以继续'细分'为的'有Eden空间、From Survivor空间、To Survivor空间'等。

    根据Java虚拟机规范的规定，'Java堆'可以'处于物理上不连续的内存空间中'，只要'逻辑上是连续的即可'，就像我们的'磁盘空间'一样。
    在实现时，既可以实现成固定大小的，也可以是可扩展的，不过'当前主流的虚拟机'都是'按照可扩展来实现的'（通过-Xmx和-Xms控制）。

    如果'在堆中没有内存完成实例分配'，并且'堆也无法再扩展时'，将'会抛出OutOfMemoryError异常'。

方法区（Method Area）

    '方法区'（Method Area）与Java堆一样，是各个'线程共享的内存区域'，它用于存储已被虚拟机加载的'类信息、常量、静态变量、即时编译器编译后的代码'等数据。
    虽然Java虚拟机规范'把方法区描述为' '堆的一个逻辑部分'，但是它却有一个别名叫做Non-Heap（非堆），目的应该'是与Java堆区分开来'。

    对于习惯在HotSpot虚拟机上开发和部署程序的开发者来说，很多人愿意'把方法区称为“永久代”'（Permanent Generation），
    本质上'两者并不等价'，仅仅是因为HotSpot虚拟机的设计团队'选择把GC分代收集扩展至方法区'，或者说'使用永久代来实现方法区'而已。

    'Java虚拟机规范'对这个'区域的限制' '非常宽松'，除了和Java堆一样'不需要连续的内存'和'可以选择固定大小'或者'可扩展'外，
    还可以'选择不实现垃圾收集'。相对而言，'垃圾收集行为' '在这个区域'是'比较少出现'的，但'并非数据进入了方法区' 就如永久代的名字一样“永久”存在了。
    这个'区域的内存回收目标'主要是'针对常量池的回收和对类型的卸载'，一般来说'这个区域的回收“成绩”'比较'难以令人满意'，
    尤其是'类型的卸载'，条件相当苛刻，但是'这部分区域的回收'确实是有必要的。

    根据Java虚拟机规范的规定，'当方法区无法满足内存分配需求时，将抛出OutOfMemoryError异常'。
    方法区有时被称为持久代（PermGen）。
    所有的'对象在实例化后的整个运行周期内'，都'被存放在堆内存中'。
    堆内存又被划分成不同的部分：伊甸区(Eden)，幸存者区域(Survivor Sapce)，老年代（Old Generation Space）。

    '方法的执行都是伴随着线程的'。'原始类型的本地变量'以及'引用'都'存放在线程栈中'。
    而'引用关联的对象'比如String，都'存在在堆中'。
    
    为了更好的理解上面这段话，我们可以看一个例子：

    import java.text.SimpleDateFormat;
    import java.util.Date;
    import org.apache.log4j.Logger;
    
    public class HelloWorld {
        private static Logger LOGGER = Logger.getLogger(HelloWorld.class.getName());
        public void sayHello(String message) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.YYYY");
            String today = formatter.format(new Date());
            LOGGER.info(today + ": " + message);
        }
    }
    这段程序的数据在内存中的存放如下：

    通过'JConsole工具'可以'查看运行中的Java程序'（比如Eclipse）'的一些信息'：'堆内存的分配'，'线程的数量'以及'加载的类的个数'；



程序计数器（Program Counter Register）
    程序计数器（Program Counter Register）是一块较小的内存空间，它的作用可以看做是当前线程所执行的字节码的行号指示器。在虚拟机的概念模型里（仅是概念模型，各种虚拟机可能会通过一些更高效的方式去实现），字节码解释器工作时就是通过改变这个计数器的值来选取下一条需要执行的字节码指令，分支、循环、跳转、异常处理、线程恢复等基础功能都需要依赖这个计数器来完成。

    由于Java虚拟机的多线程是通过线程轮流切换并分配处理器执行时间的方式来实现的，在任何一个确定的时刻，一个处理器（对于多核处理器来说是一个内核）只会执行一条线程中的指令。因此，为了线程切换后能恢复到正确的执行位置，每条线程都需要有一个独立的程序计数器，各条线程之间的计数器互不影响，独立存储，我们称这类内存区域为“线程私有”的内存。

    如果线程正在执行的是一个Java方法，这个计数器记录的是正在执行的虚拟机字节码指令的地址；如果正在执行的是Natvie方法，这个计数器值则为空（Undefined）。

    此内存区域是唯一一个在Java虚拟机规范中没有规定任何OutOfMemoryError情况的区域。

JVM栈（JVM Stacks）
    '与程序计数器一样'，'Java虚拟机栈'（Java Virtual Machine Stacks）也是'线程私有的'，它的'生命周期与线程相同'。
    '虚拟机栈' 描述的是 'Java方法执行的内存模型'：'每个方法被执行的时候' '都会同时创建一个栈帧'（Stack Frame）
    用于存储'局部变量表、操作栈、动态链接、方法出口'等信息。
    '每一个方法' '被调用直至执行完成的过程'，就对应着一个栈帧在虚拟机栈中从入栈到出栈的过程。

    '局部变量表'存放了'编译期可知的各种基本数据类型'（boolean、byte、char、short、int、float、long、double）、
    '对象引用'（reference类型，它'不等同于对象本身'，根据'不同的虚拟机实现'，它可能是一个'指向对象起始地址的引用指针'，也可能指向一个'代表对象的句柄'或者其他'与此对象相关的位置'）
    和'returnAddress'（'指向了一条字节码指令的地址'）。

    其中'64位长度的long和double类型'的数据会'占用2个局部变量空间（Slot）'，其余的数据类型'只占用1个'。
    '局部变量表所需的内存空间' '在编译期间完成分配'，
    当进入一个方法时，这个方法'需要在帧中分配多大的局部变量空间' '是完全确定的'，
    '在方法运行期间' '不会改变局部变量表的大小'。

    在Java虚拟机规范中，对这个区域规定了'两种异常状况'：
    如果'线程请求的栈深度'大于'虚拟机所允许的深度'，将'抛出StackOverflowError异常'；
    如果'虚拟机栈可以动态扩展'（当前大部分的Java虚拟机都可动态扩展，只不过Java虚拟机规范中也允许固定长度的虚拟机栈），
    当扩展时'无法申请到足够的内存'时会'抛出OutOfMemoryError异常'。

本地方法栈（Native Method Stacks）

    本地方法栈（Native Method Stacks）与'虚拟机栈所发挥的作用'是非常相似的，
    '其区别'不过'是虚拟机栈'为'虚拟机执行Java方法'（也就是字节码）服务，
    而'本地方法栈'则是'为虚拟机使用到的Native方法服务'。
    '虚拟机规范'中对'本地方法栈中的方法' '使用的语言、使用方式与数据结构' 并'没有强制规定'，因此'具体的虚拟机' '可以自由实现'它。
    甚至有的虚拟机（譬如'Sun HotSpot虚拟机'）直接就把'本地方法栈'和'虚拟机栈' '合二为一'。
    与虚拟机栈一样，本地方法栈区域也会抛出'StackOverflowError'和'OutOfMemoryError'异常。

    OutOfMemoryError
    '对内存结构清晰的认识'同样可以'帮助理解不同OutOfMemoryErrors'：

    Exception in thread “main”: java.lang.OutOfMemoryError: 'Java heap space'
    原因：'对象不能被分配到堆内存中'

    Exception in thread “main”: java.lang.OutOfMemoryError: 'PermGen space'
    原因：'类或者方法' '不能被加载到持久代'。它可能出现在'一个程序加载很多类的时候'，比如引用了很多第三方的库；

    Exception in thread “main”: java.lang.OutOfMemoryError: 'Requested array size exceeds VM limit'
    原因：'创建的数组大于堆内存的空间'

    Exception in thread “main”: java.lang.OutOfMemoryError: 'request <size> bytes for <reason>. Out of swap space?'
    [没有看懂，略过]原因：分配本地分配失败。JNI、本地库或者Java虚拟机都会从本地堆中分配内存空间。

    Exception in thread “main”: java.lang.OutOfMemoryError: '<reason> <stack trace>（Native method）'
    原因：同样是'本地方法内存分配失败'，只不过是JNI或者本地方法或者Java虚拟机发现




[评论]
在jdk1.8中，永久代已经不存在，存储的类信息、编译后的代码数据等已经移动到了'元空间'（MetaSpace）中，
'元空间并没有处于堆内存上'，而是'直接占用的本地内存'（NativeMemory）。

'元空间的本质'和永久代类似，都是'对JVM规范中方法区的实现'。
不过元空间与永久代之间最大的区别在于：'元空间'并不在虚拟机中，而'是使用本地内存'。
因此，默认情况下，'元空间的大小'仅'受本地内存限制'，但可以通过以下'参数'来'指定元空间的大小'：
　　-XX:MetaspaceSize，'初始空间大小'，'达到该值'就'会触发垃圾收集' 进行'类型卸载'，同时GC会对该值进行调整：如果释放了大量的空间，就适当降低该值；如果释放了很少的空间，那么在不超过MaxMetaspaceSize时，适当提高该值。
　　-XX:MaxMetaspaceSize，'最大空间'，默认是没有限制的。
除了上面两个指定大小的选项以外，还有'两个与 GC 相关的属性'：
　　-XX:MinMetaspaceFreeRatio，在GC之后，最小的Metaspace剩余空间容量的百分比，减少为分配空间所导致的垃圾收集
　　-XX:MaxMetaspaceFreeRatio，在GC之后，最大的Metaspace剩余空间容量的百分比，减少为释放空间所导致的垃圾收集
一次Java线程池误用引发的血案和总结

前言背景
    这是一个十分严重的问题
    自从最近的某年某月某天起，线上服务开始变得不那么稳定。
    在高峰期，时常有几台机器的内存持续飙升，并且无法回收，导致服务不可用。

    例如GC时间采样曲线：


    和内存使用曲线：


    图中所示，18:50-19:00的阶段，已经处于'服务不可用的状态'了。上游服务的'超时异常'会增加，该台机器会'触发熔断'。
    熔断触发后，'该台机器的流量'会打到'其他机器'，其他机器发生类似的情况的可能性会提高，极端情况会引起所有服务宕机，曲线掉底。

    因为线上内存过大，如果采用 'jmap dump'的方式，这个任务可能需要很久才可以执行完，
    同时把这么大的文件存放起来导入工具也是一件很难的事情。
    再看JVM启动参数，也很久没有变更过 Xms, Xmx, -XX:NewRatio, -XX:SurvivorRatio, 
    虽然没有仔细分析程序使用内存情况，但看起来也无大碍。

    于是开始找代码，某年某天某月～ 嗯，注意到一段这样的代码提交：

    private static ExecutorService executor = Executors.newFixedThreadPool(15);
    public static void push2Kafka(Object msg) {
        executor.execute(new WriteTask(msg,  false));    
    }

    相关代码的完整功能是，每次线上调用，都会把计算结果的日志打到 Kafka，Kafka消费方再继续后续的逻辑。内存被耗尽可能有一个原因是，
    因为使用了 newFixedThreadPool 线程池，而它的工作机制是，固定了N个线程，而提交给线程池的'任务队列'是'不限制大小'的，
    如果'Kafka发消息' '被阻塞或者变慢'，那么'显然队列里面的内容' '会越来越多'，也就会导致这样的问题。

    为了验证这个想法，做了个小实验，把 newFixedThreadPool 线程池的'线程个数'调小一点，例如 1。果然'压测'了一下，很快就复现了'内存耗尽'，'服务不可用'的悲剧。

    最后的'修复策略'是'使用了自定义的线程池参数'，而非 Executors 默认实现解决了问题。下面就'把线程池相关的原理'和'参数'总结一下，避免未来踩坑。

1. Java线程池

    
    线程池顾名思义，就是'由很多线程构成的池子'，来一个任务，就从池子中取一个线程，'处理这个任务'。
    这个理解是我在第一次接触到这个概念时候的理解，虽然整体基本切入到核心，但是实际上会比这个复杂。
    
    例如'线程池肯定不会无限扩大的'，否则'资源会耗尽'；
    当线程数到达一个阶段，'提交的任务会被暂时存储在一个队列中'，'如果队列可以不断扩大'，
    极端下也会耗尽资源，那选择什么类型的队列，当'队列满' '如何处理任务'，都有涉及很多内容。线程池总体的工作过程如下图：


    '线程池内的线程数量' '相关的概念'有两个，一个是'核心池大小'，还有'最大池大小'。
    
    如果当前的线程个数比核心池个数小，当任务到来，会优先'创建一个新的线程并执行任务'。
    当已经到达核心池大小，则'把任务放入队列'，为了资源不被耗尽，'队列的最大容量'可能也是'有上限'的，
    如果'达到队列上限'则'考虑继续创建新线程' '执行任务'，如果此刻线程的个数已经到达最大池上限，则'考虑把任务丢弃'。

    在 java.util.concurrent 包中，提供了 ThreadPoolExecutor 的实现。下面是ThreadPoolExecutor的构造方法签名

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler)
    
    既然有了刚刚对线程池工作原理对概述，这些参数就很容易理解了：

    'corePoolSize'
        核心池大小，既然如前原理部分所述。需要注意的是'在最开始' '创建线程池时' '线程不会立即被创建'，
        直到'有任务提交'才'开始创建线程'，当线程数目'达到corePoolSize时候'就'停止线程创建'。
        '若想一开始就创建' '所有核心线程'， '需调用prestartAllCoreThreads方法'。

    'maximumPoolSize' 
        池中允许的'最大线程数'。需要注意的是'当核心线程满'且'阻塞队列也满'时才会判断'当前线程数' '是否小于' '最大线程数'，并'决定' '是否' '创建新线程'。

    'keepAliveTime' 
        当全部线程数'大于'核心线程数时，多余的空闲线程最多存活时间

    'unit' - keepAliveTime 参数的'时间单位'。

    'workQueue'
        当'线程数目'超过'核心线程数'时'用于保存任务的队列'。主要有'3种类型'的BlockingQueue可供选择：
        '无界队列'，'有界队列'和'同步移交'。将在下文中详细阐述。
        从参数中可以看到，此'队列仅保存' '实现Runnable接口的任务'。 
        别看这个参数位置很靠后，但是真的很重要，因为楼主的坑就因这个参数而起，这些细节有必要仔细了解清楚。

    'threadFactory' - 执行程序创建新线程时'使用的工厂'。

    'handler'
        '阻塞队列已满'且'线程数达到最大值'时所采取的'饱和策略'。java默认提供了'4种饱和策略'的实现方式：'中止'、'抛弃'、'抛弃最旧的'、'调用者运行'。

2. 可选择的'阻塞队列'BlockingQueue详解

        在重复一下'新任务'进入线程池时的执行策略：
        如果运行的线程少于corePoolSize，则 Executor始终首选'添加新的线程'，而'不进行排队'。（如果当前运行的线程小于corePoolSize，则任务不会存入queue中，而是直接运行）
        如果'运行的线程大于等于 corePoolSize'，则 Executor始终首选将请求'加入队列'，而'不添加新的线程'。
        如果'无法将请求加入队列'，则'创建新的线程'，除非创建此线程会使得'线程数量超出 maximumPoolSize'，在这种情况下，'任务将被拒绝'。

        主要有3种类型的BlockingQueue：

        '无界队列'

            '队列大小' '默认为Integer.MAX_VALUE'，常用的为无界的LinkedBlockingQueue，使用该队列做为阻塞队列时要尤其当心，
            如果存在'添加速度'大于'删除速度'时候，有可能会'内存溢出'。
            当'任务耗时较长'时可能会'导致大量新任务' '在队列中堆积' '最终导致OOM'。
            为了避免队列过大造成机器负载或者内存爆满的情况出现，我们在使用的时候建议手动传一个队列的大小。
            

            阅读代码发现，Executors.newFixedThreadPool 采用就是 LinkedBlockingQueue，而楼主踩到的就是这个坑，
            '当QPS很高'，'发送数据很大'，'大量的任务'被添加到这个'无界LinkedBlockingQueue' 中，导致'cpu和内存飙升' 服务器挂掉。

        '有界队列'

            '常用的有两类'，
            一类是'遵循FIFO原则的队列'如'ArrayBlockingQueue',, (ps:'LinkedBlockingQueue'，也可以算是FIFO的，其可以是无界，也可以是有界)
            另一类是'优先级队列'如'PriorityBlockingQueue'。PriorityBlockingQueue中的'优先级由任务的Comparator决定'。
            使用有界队列时'队列大小' '需和线程池大小' '互相配合'，'线程池较小' '有界队列较大'时可'减少内存消耗'，降低cpu使用率和上下文切换，但是'可能会限制系统吞吐量'。

            在我们的修复方案中，选择的就是这个类型的队列，虽然'会有部分任务被丢失'，但是我们场景是'排序日志搜集任务'，所以'对部分对丢失是可以容忍的'。

        '同步移交队列'

            如果'不希望任务在队列中等待'而是希望'将任务直接移交给工作线程'，可使用SynchronousQueue作为等待队列。
            SynchronousQueue不是一个真正的队列，而是一种'线程之间移交的机制'。
            如果'要将元素' '成功放入SynchronousQueue中'，
            必须'有另一个线程' '正在等待接收这个元素'。只有在使用无界线程池或者有饱和策略时'才建议使用该队列'。

3. 可选择的'饱和策略' 'RejectedExecutionHandler' 详解
    JDK主要提供了'4种饱和策略'供选择。4种策略都'做为静态内部类'在ThreadPoolExcutor中进行实现。

    3.1 'AbortPolicy中止策略'

        该策略是'默认饱和'策略。

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " +  e.toString());
        } 

        '使用该策略时' '在饱和时' '会抛出RejectedExecutionException'（继承自RuntimeException），调用者'可捕获该异常自行处理'。

    3.2 DiscardPolicy抛弃策略

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }

        如代码所示，'不做任何处理直接抛弃任务'

    3.3 DiscardOldestPolicy'抛弃旧任务策略'
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    if (!e.isShutdown()) {
                        e.getQueue().poll();
                        e.execute(r);
                    }
        } 
        如代码，先将阻塞队列中的'头元素出队'抛弃，'再尝试提交任务'。
        如果此时'阻塞队列使用PriorityBlockingQueue优先级队列'，将'会导致优先级最高的任务'被抛弃，因此'不建议将该种策略'配合'优先级队列'使用。

    3.4 CallerRunsPolicy调用者运行
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    if (!e.isShutdown()) {
                        r.run();
                    }
        } 
    既'不抛弃任务也不抛出异常'，'直接运行任务的run方法'，换言之'将任务回退给调用者线程' '来直接运行'。
    使用该策略时'线程池饱和后'将由调用线程池的主线程自己来执行任务，'因此在执行任务的这段时间'里'主线程无法再提交新任务'，
    从而'使线程池中工作线程' '有时间将正在处理的任务处理完成'。

4. Java提供的四种常用线程池解析
既然楼主踩坑就是使用了 JDK 的默认实现，那么再来看看这些默认实现到底干了什么，封装了哪些参数。
简而言之 

Executors 工厂方法Executors.newCachedThreadPool() 提供了无界线程池，可以进行自动线程回收；
Executors.newFixedThreadPool(int) 提供了固定大小线程池，内部使用无界队列；
Executors.newSingleThreadExecutor() 提供了单个后台线程。

详细介绍一下上述四种线程池。

4.1 newCachedThreadPool
public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
} 

'作用'：在newCachedThreadPool中'如果线程池长度超过处理需要'，'可灵活回收空闲线程'，'若无可回收，则新建线程'。

['疑惑']
    初看该构造函数时'我有这样的疑惑'：核心线程池为0，那按照前面所讲的线程池策略'新任务来临时无法进入核心线程池'，
    只能进入 SynchronousQueue中进行'等待'，'而SynchronousQueue的大小为1'，那岂不是'第一个任务到达时' '只能等待在队列中'，
    直到第二个任务到达发现无法进入队列才能创建第一个线程？

    这个问题的答案在上面讲SynchronousQueue时其实已经给出了，要将一个元素放入SynchronousQueue中，必须有另一个线程正在等待接收这个元素。
    因此即便SynchronousQueue一开始为空且大小为1，'第一个任务也无法放入其中'，因为'没有线程在等待'从SynchronousQueue中取走元素。
    因此'第一个任务到达时' '便会创建一个新线程执行该任务'。

4.2 newFixedThreadPool
 public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>());
 }
看代码一目了然了，线程数量固定，使用无限大的队列。再次强调，楼主就是踩的这个无限大队列的坑。

4.3 newScheduledThreadPool
创建一个定长线程池，支持'定时及周期性任务'执行。

public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
}
在来看看ScheduledThreadPoolExecutor（）的构造函数

 public ScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
              new DelayedWorkQueue());
    } 
ScheduledThreadPoolExecutor的父类即ThreadPoolExecutor，因此这里各参数含义和上面一样。值得关心的是DelayedWorkQueue这个阻塞对列，在上面没有介绍，它作为静态内部类就在ScheduledThreadPoolExecutor中进行了实现。简单的说，DelayedWorkQueue是一个无界队列，它能按一定的顺序对工作队列中的元素进行排列。

4.4 newSingleThreadExecutor
创建一个单线程化的线程池，它只会用唯一的工作线程来执行任务，保证所有任务按照指定顺序(FIFO, LIFO, 优先级)执行。

public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new DelegatedScheduledExecutorService
            (new ScheduledThreadPoolExecutor(1));
 } 
首先new了一个线程数目为 1 的ScheduledThreadPoolExecutor，再把该对象传入DelegatedScheduledExecutorService中，
看看DelegatedScheduledExecutorService的实现代码：

DelegatedScheduledExecutorService(ScheduledExecutorService executor) {
            super(executor);
            e = executor;
} 
在看看它的父类

DelegatedExecutorService(ExecutorService executor) { 
           e = executor; 
} 
其实就是使用装饰模式增强了ScheduledExecutorService（1）的功能，不仅确保只有一个线程顺序执行任务，也保证线程意外终止后会重新创建一个线程继续执行任务。

结束语
虽然之前学习了不少相关知识，但是只有在实践中踩坑才能印象深刻吧

编辑于 2018-01-12 20:33












分享
文章被以下专栏收录
不懂机器学习的架构师不是好CTO
不懂机器学习的架构师不是好CTO
推荐阅读
由于不知道Java线程池的bug,某程序员叕被祭天
说说你对线程池的理解？首先明确，池化的意义在于 缓存，创建性能开销较大的对象，比如线程池、连接池、内存池。预先在池里创建一些对象，使用时直接取，用完就归还复用，使用策略调整池中…

JavaEdge
Java并发/Executor并发框架/线程池，ThreadToolExecutor初步理解
Java并发/Executor并发框架/线程池，ThreadToolExecutor初步理解
极乐君
发表于极乐科技
Java高频面试题：你使用过线程池吗？
Java高频面试题：你使用过线程池吗？
半情调
发表于java学...
线程池里的大学问：分析Java线程池的创建
线程池里的大学问：分析Java线程池的创建
极乐君
发表于极乐科技

44 条评论
​切换为时间排序
写下你的评论...



发布
精选评论（1）
贺小五
贺小五2018-01-13
我觉得，有两个问题，一个是线程池，另一个就是kafka，kafka不限制大小，因为线程池消费不过来导致这种情况，不单单解决线程池就行了，还要解决kafka，设置长度，还有阈值，超过多少就应该报警了
​4
​回复
​踩
​ 举报
评论（44）
奔跑的大基蛋
奔跑的大基蛋2018-01-13
Alibaba命名规范的解释：

【强制】线程池不允许使用 Executors 去创建，而是通过 ThreadPoolExecutor的方式，这样 的处理方式让写的同学更加明确线程池的运行规则，规避资源耗尽的风险。 说明： Executors 返回的线程池对象的弊端如下： 1） FixedThreadPool 和 SingleThreadPool : 允许的请求队列长度为 Integer.MAX_VALUE ，可能会堆积大量的请求，从而导致 OOM 。 2） CachedThreadPool 和 ScheduledThreadPool : 允许的创建线程数量为 Integer.MAX_VALUE ，可能会创建大量的线程，从而导致 OOM 。

​31
​回复
​踩
​ 举报
小叮当的耳朵
小叮当的耳朵回复奔跑的大基蛋2018-07-05
那么请问大佬，ScheduledThreadPool的Integer.MAX_VALUE问题应该应该怎么解决？

​赞
​回复
​踩
​ 举报
Arthas
Arthas回复小叮当的耳朵2021-07-10
生产环境所有涉及多线程的问题都使用自定义线程池
​1
​回复
​踩
​ 举报
展开其他 1 条回复
菊花怪
菊花怪2019-09-29
JDK8中的ThreadPoolExecutor不应该是coreThread满了以后优先放入队列么,也就是说如果队列是无界的,他是不会自己扩充线程的

/*
* Proceed in 3 steps:
*
* 1. If fewer than corePoolSize threads are running, try to
* start a new thread with the given command as its first
* task. The call to addWorker atomically checks runState and
* workerCount, and so prevents false alarms that would add
* threads when it shouldn't, by returning false.
*
* 2. If a task can be successfully queued, then we still need
* to double-check whether we should have added a thread
* (because existing ones died since last checking) or that
* the pool shut down since entry into this method. So we
* recheck state and if necessary roll back the enqueuing if
* stopped, or start a new thread if there are none.
*
* 3. If we cannot queue task, then we try to add a new
* thread. If it fails, we know we are shut down or saturated
* and so reject the task.
*/

​2
​回复
​踩
​ 举报
阿贝
阿贝2018-01-13
你好题主，有个问题想请教下。
我映像中kafka的accumulator是有一个bufferpool的，可以设置大小，还有一个设置send方法阻塞的timeout的。
如果生产消息快于消费消息，那么消息会在buffer聚集，当buffer爆了之后send方法在一定时间后会抛出timeout异常。
所以根据你的描述，1，你们遇到过这种异常(情况)吗？ 2，或者说你们对这种异常进行了额外处理？
因为我们也想用kafka发送些东西，在做kafka的性能测试，就怕它OOM～
​2
​回复
​踩
​ 举报
唐三水
唐三水回复阿贝2019-06-11
mark 你这个问题 也想知道答案
​赞
​回复
​踩
​ 举报
没人看的见
没人看的见回复唐三水2020-04-22
这个在启动时server-start.sh去增加JVM大小，如果还是出现生产者效率大于消费者消费速度，也可能是sender线程的request发送网络问题，可以排查一下网络速率，消息累加器是一个问题，消息发送器和IO回调通知注册也是一个排查点

​赞
​回复
​踩
​ 举报
展开其他 1 条回复
知乎用户XE2QBV
知乎用户XE2QBV2018-03-25
线程池只是将问题暴露出来了，并不一定是线程池的问题 。是不是应该考虑上游为什么创建那么多任务？为什么没控制任务数量
​1
​回复
​踩
​ 举报
雁南归
雁南归2018-01-14
这个问题产生的真正原因并不是线程池吧，而且kafka线程的消费能力不足，把固定线程池替换掉并没有本质上解决问题，业务压力大时内存还是会迅速耗尽的。个人觉得正确的做法是是投递时可以将多个msg一次批量投递，减少网络io的开销，这个应该才是消费能力不足的元凶
​1
​回复
​踩
​ 举报
文西
文西 (作者) 回复雁南归2018-01-14
这也是个不错的优化方向 谢谢
​赞
​回复
​踩
​ 举报
大脑艾瑞克
大脑艾瑞克2018-01-13
其实我更好奇，为什么一直好好的，也没改动代码，就突然出问题了。这种情况经常遇到吗？
​1
​回复
​踩
​ 举报
文西
文西 (作者) 回复大脑艾瑞克2018-01-13
可以看外界变化，部署环境，访问量等
​赞
​回复
​踩
​ 举报
shawshank
shawshank2018-01-12
可以给下现在你们用的是哪种线程池吗，是自己定制的吗？
​1
​回复
​踩
​ 举报
知乎用户FxPofQ
知乎用户FxPofQ回复shawshank2018-01-13
手册说无论如何都要自己实现线程池

​赞
​回复
​踩
​ 举报
shawshank
shawshank回复知乎用户FxPofQ2018-01-13
就是说默认的几种线程池设定都不合理对吧？
​赞
​回复
​踩
​ 举报
查看全部 9 条回复
lyn
lyn2019-03-23
PriorityBlockingQueue应该是无界的，在源码的注释里说是"An unbounded blocking queue"

​赞
​回复
​踩
​ 举报
锟斤拷
锟斤拷2018-11-02
SynchronousQueue的大小不是1，而是0

​赞
​回复
​踩
​ 举报
浩克
浩克2018-09-03
LinkedBlockingQueue 这个是有界队列吧，您在无界和有界中同时都说了。。

​赞
​回复
​踩
​ 举报
看东山
看东山​回复浩克2019-03-08
LindedBlockingQueue指定长度就是有界的哦...

​赞
​回复
​踩
​ 举报
王浩
王浩2018-03-19
问题的本质不在于线程池，在于Kafka客户端在通信的时候居然会长阻塞，你至少也要有基本的timeout时间啊
​赞
​回复
​踩
​ 举报
文西
文西 (作者) 回复王浩2018-03-19
那是个公司封装的包，里面很多坑
​赞
​回复
​踩
​ 举报
李平
李平2018-01-16
最重要的如何定位问题，一笔带过！！！！

​赞
​回复
​踩
​ 举报
文西
文西 (作者) 回复李平2018-01-16
git上撸出来的

​赞
​回复
​踩
​ 举报
鼠先生
鼠先生2018-01-13
IDEA 也提示不让用 Executors去创建线程池
​赞
​回复
​踩
​ 举报
Alex Wang
Alex Wang​2018-01-13
嗯，好
​赞
​回复
​踩
​ 举报
筱龙缘
筱龙缘2018-01-13
知乎上有几篇文章讲并发包 可以搜搜看
​赞
​回复
​踩
​ 举报
文西
文西 (作者) 回复筱龙缘2018-01-13
之前没注意

​赞
​回复
​踩
​ 举报
跳刀敌法
跳刀敌法2018-01-13
mark
​赞
​回复
​踩
​ 举报
ShallNotPass
ShallNotPass2018-01-12
我这边的应用也用了大量的fixedThreadPool，看来是时候查一波了
​赞
​回复
​踩
​ 举报
Dirax
Dirax2018-01-12
这个问题似乎在阿里出的那个java手册里有提到过，说禁止使用Executors的方法，就是因为默认的任务队列长度的原因。
​赞
​回复
​踩
​ 举报
文西
文西 (作者) 回复Dirax2018-01-12
是的，也有人提过这个手册，不过当时没注意看哈哈
​赞
​回复
​踩
​ 举报
Dirax
Dirax回复文西 (作者)2018-01-12
我也是看了这个才开始注意这个点.
​赞
​回复
​踩
​ 举报
展开其他 2 条回复

选择语言

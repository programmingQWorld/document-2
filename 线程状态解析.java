在我们接触编程时，就开始接触各种生命周期，比如对象的生命周期，程序的生命周期等等，对于线程来说也是'存在自己的生命周期'，
而且这也是面试与我们深入了解多线程必备的知识，今天我们主要'介绍线程的生命周期'及其各种状态的转换。
'线程的六种状态'

线程的生命周期主要有以下六种状态：

New（新创建）
Runnable（可运行）
Blocked（被阻塞）
Waiting（等待）
Timed Waiting（计时等待）
Terminated（被终止）

在我们程序编码中如果想要确定线程当前的状态，可以通过'getState()方法'来获取，同时我们需要注意'任何线程在任何时刻都只能是处于一种状态'。

线程状态介绍
    New 新建状态
        首先我们展示一下整个'线程状态的转换流程图，'下面我们将进行详细的介绍讲解， 
        
        New表示'线程被创建但尚未启动'的状态：当我们用 new Thread()新建一个线程时，
        而一旦线程调用了start(),  它的状态就会 '从 New 变成 Runnable'，进入到图中绿色的方框

    Runnable 可运行状态
        'Java中的Runable状态'对应'操作系统线程状态中的两种状态'，
        分别是 'Running' 和 'Ready'，
        
        也就是说，Java 中处于 Runnable 状态的线程'有可能正在执行'，
        '也有可能' '没有正在执行'，正在'等待被分配 CPU 资源'。
        
        所以，如果一个正在运行的线程是 Runnable 状态，
        当它运行到任务的一半时，执行该线程的 'CPU 被调度去做其他事情'，
        导致该线程暂时不运行，它的状态依然不变，还是 Runnable，
        因为'它有可能随时被调度回来继续执行任务'。
        
    '阻塞状态'

        这三个状态我们可以'统称为阻塞状态'，它们分别是 Blocked(被阻塞)、Waiting(等待)、Timed Waiting(计时等待) .


    Blocked 被阻塞状态

        '从 Runnable 状态进入到 Blocked 状态'只有'一种途径'，
        
        '当进入到 synchronized 代码块中时' '未能获得相应的 monitor 锁'

        在右侧我们可以看到，'有连接线' '从 Blocked 状态指向了 Runnable' ，也只有一种情况，
        
        那么就是'当线程获得 monitor 锁'，此时线程就会'进入 Runnable 状态'中参与 CPU 资源的抢夺

    Waiting 等待状态

        对于 'Waiting 状态的进入' '有三种情况'，分别为：

            当线程中'调用了' 没有设置 Timeout 参数的 'Object.wait()' 方法 ('挂起当前线程')
            当线程调用了没有设置 Timeout 参数的 'Thread.join()' 方法
            当线程调用了 'LockSupport.park()' 方法

        关于 LockSupport.park() 方法，这里说一下，我们通过上面知道 Blocked 是针对 synchronized monitor 锁的，
        
        但是在 Java 中实际是'有很多其他锁'的，
        
        比如 ReentrantLock 等，在这些锁中，
        '如果线程没有获取到锁'则'会直接进入 Waiting 状态'，
        其实'这种本质上'它就'是执行了 LockSupport.park() 方法'进入了Waiting 状态

    'Blocked' 与 'Waiting 的区别'
        Blocked 是'在等待其他线程' '释放 monitor 锁'
        Waiting 则是在等待某个条件，比如 'join 的线程执行完毕'，或者是 notify()/notifyAll()。

    Timed Waiting 计时等待状态
        最后我们来说说这个 Timed Waiting 状态，它与 Waiting 状态非常相似，
        其中的区别只在于'是否有时间的限制'，在 Timed Waiting 状态时会'等待超时'，
        之后'由系统唤醒'，或者也可以'提前被通知唤醒'如 notify



        以下情况会让线程进入 Timed Waiting状态。
            线程执行了设置了'时间参数'的 Thread.sleep(long millis) 方法；
            线程执行了设置了'时间参数'的 Object.wait(long timeout) 方法；
            线程执行了设置了'时间参数'的 Thread.join(long millis) 方法；
            线程执行了设置了'时间参数'的 LockSupport.parkNanos(long nanos) 方法和 LockSupport.parkUntil(long deadline) 方法。



线程状态间转换

    上面我们讲了'各自状态的特点'和运行状态'进入相应状态'的情况 ，
    那么接下来我们将来分析'各自状态之间的转换'，
    其实主要就是 Blocked、waiting、Timed Waiting 三种状态的转换 ，
    以及他们是如何进入下一状态最终进入 Runnable

Blocked 进入 Runnable

    必须要'线程获得 monitor 锁'，但是如果'想进入其他状态'就'相对比较特殊'，
    因为它是'没有超时机制'的，也就是'不会主动进入'。


Waiting 进入 Runnable

    只有当执行了'LockSupport.unpark()'，或者 'join 的线程运行结束'，
    '或者被中断时'才可以'进入 Runnable 状态'。

    如果通过其他线程调用 notify() 或 notifyAll() 来唤醒它，
    则它会直接进入 Blocked 状态，这里大家可能会有疑问，不是应该直接进入 Runnable 吗？

        这里需要注意一点 ，因为唤醒 Waiting 线程的线程如果调用 notify() 或 notifyAll()，
        要求必须首先持有该 monitor 锁，
        这也就是我们说的 wait()、notify 必须在 synchronized 代码块中。

        所以'处于 Waiting 状态的线程' '被唤醒时拿不到该锁'，就'会进入 Blocked 状态'，
        直到执行了 notify()/notifyAll() 的'唤醒它的线程执行完毕并释放 monitor 锁'，
        才可能轮到它去竞争这把锁，如果它能抢到，就会'从 Blocked 状态回到 Runnable 状态'。

        这里大家一定要注意这点，当我们通过 notify 唤醒时，是先进入阻塞状态的 ，
        再等抢夺到 monitor 锁喉才会进入 Runnable 状态！

Timed Waiting 进入 Runnable

    同样在 Timed Waiting 中执行 notify() 和 notifyAll()也是一样的道理，
    它们会先进入 Blocked 状态，然后抢夺锁成功后，再回到 Runnable 状态。

    但是对于 Timed Waiting 而言，它'存在超时机制'，也就是说如果超时时间到了那么就会'系统自动直接拿到锁'，

    或者'当 join 的线程执行结束'/'调用了LockSupport.unpark()'/'被中断等情况'
    都'会直接进入 Runnable 状态'，而'不会经历 Blocked 状态'


Terminated 终止

最后我们来说最后一种状态，Terminated 终止状态，要想进入这个状态有两种可能。

run() 方法'执行完毕'，线程'正常退出'。
'出现一个没有捕获的异常'，终止了 run() 方法，最终导致'意外终止'。

总结

最后我们说一下再看'线程转换的过程'中一定'要注意两点'：

线程的状态是'按照箭头方向'来走的，
比如线程从 New状态是不可以直接进入 Blocked 状态的，它'需要先经历 Runnable 状态'。

线程'生命周期不可逆'：
一旦进入 Runnable 状态就不能回到 New 状态；
一旦被终止就不可能再有任何状态的变化。

所以一个线程只能有一次 New和 Terminated状态，
'只有处于中间状态' '才可以相互转换'。
也就是'这两个状态不会参与相互转化'

java new一个对象的过程中发生了什么

[作者]天风

java在new一个对象的时候，会先查看对象所属的类有没有被加载到内存，
如果没有的话，就会'先通过类的全限定名'来加载。加载并初始化类完成后，再'进行对象的创建工作'。

我们先假设是第一次使用该类，这样的话'new一个对象'就可以'分为两个过程'：'加载并初始化类'和'创建对象'。

一、类加载过程（第一次使用该类）

    java是'使用双亲委派模型'来'进行类的加载的'，所以'在描述类加载过程前'，我们'先看一下它的工作过程'：

    '双亲委托模型的工作过程'是：
        
        如果一个'类加载器'（ClassLoader）'收到'了'类加载的请求'，
        它首先'不会自己去尝试加载'这个类，而是'把这个请求' '委托给父类加载器'去完成，'每一个层次的类加载器'都是如此，
        因此'所有的加载请求'最终都应该'传送到' '顶层的启动类加载器'中，
        只有当'父类加载器'反馈自己'无法完成这个加载请求'（它的搜索范围中没有找到所需要加载的类）时，'子加载器才会尝试自己去加载'。

    使用双亲委托机制的'好处是'：
    '能够有效确保' '一个类的全局唯一性'，当程序中出现'多个限定名相同的类'时，'类加载器在执行加载时'，'始终只会加载其中的某一个类'。

    1、加载

        由'类加载器'负责根据一个'类的全限定名'来读取此'类的二进制字节流'到'JVM内部'，
        并存储在'运行时内存区的方法区'，然后在堆中创建一个'与目标类型对应的java.lang.Class对象实例'

    2、验证

        '格式'验证：验证'是否符合class文件规范' 1. 0xCAFEBABE 2.类文件版本号

        '语义'验证：
            检查一个'被标记为final的类型' '是否包含子类'；
            检查一个'类中的final方法' '是否被子类进行重写'；
            确保'父类和子类之间' '没有不兼容的一些方法声明'（比如方法签名相同，但方法的返回值不同）

        '操作'验证：在'操作数栈'中的数据'必须进行正确的操作'，对'常量池中的各种符号引用'执行验证
            （通常'在解析阶段执行'，检查是否可以通过符号引用中描述的全限定名'定位到指定类型上'，
            以及'类成员信息的访问修饰符' '是否允许访问'等）

    3、准备

        为类中的'所有静态变量' '分配内存空间'，并'为其设置一个初始值'（由于'还没有产生对象'，实例变量不在此操作范围内）

        被'final修饰的static变量（常量），会直接赋值'；

    4、解析

        将'常量池中的符号引用'转为'直接引用'（得到类或者字段、方法'在内存中的指针或者偏移量'，以便直接调用该方法），
        这个可以'在初始化之后'再执行。

        解析'需要静态绑定的内容'。 // 所有不会被重写的方法和域都会被静态绑定

        以上2、3、4'三个阶段'又合称为'链接阶段'，链接阶段要做的是将加载到JVM中的二进制字节流的类数据信息合并到JVM的运行时状态中。

    5、初始化（先父后子）

    5.1 '为静态变量赋值'

    5.2 '执行static代码块'

    注意：'static代码块' '只有jvm能够调用'

    如果是'多线程'需要'同时初始化一个类'，仅仅只能允许其中一个线程对其执行初始化操作，其余线程必须等待，
    只有在活动线程执行完对类的初始化操作之后，才会通知正在等待的其他线程。
    

    因为'子类'存在'对父类的依赖'，所以'类的加载顺序'是'先加载父类' '后加载子类'，初始化也一样。
    不过，父类初始化时，子类静态变量的值也是有的，是默认值。

    最终，'方法区'会'存储当前类类信息'，包括'类的静态变量'、'类初始化代码'（'定义静态变量时的赋值语句' 和 '静态初始化代码块'）、
    '实例变量定义'、'实例初始化代码'（'定义实例变量时的赋值语句','实例代码块'和'构造方法'）和'实例方法'，还有'父类的类信息引用'。

二、创建对象

    1、在'堆区分配对象'需要的'内存'

        '分配的内存'包括本类和父类的'所有实例变量'，但不包括任何静态变量

    2、对所有实例变量'赋默认值'

        将'方法区内' '对实例变量的定义' '拷贝一份到堆区'，然后'赋默认值'

    3、执行实例初始化代码

    '初始化顺序'是'先初始化父类' '再初始化子类'，初始化时'先执行实例代码块' '然后是构造方法'

    4、如果有类似于Child c = new Child();形式的c引用的话，在栈区定义Child'类型引用变量c'，然后'将堆区对象的地址'赋值给它

        需要注意的是，每个子类对象持有父类对象的引用，可'在内部通过super关键字'来'调用父类对象'，但'在外部不可访问'

    补充：

    通过实例引用调用实例方法的时候，先从方法区中对象的实际类型信息找，找不到的话再去父类类型信息中找。

    如果'继承的层次比较深'，'要调用的方法'位于比较上层的父类，则'调用的效率是比较低的'，因为'每次调用都要经过很多次查找'。
    这时候大多系统会采用一种称为'虚方法表'的方法'来优化调用的效率'。

    所谓虚方法表，就是'在类加载的时候'，'为每个类创建一个表'，
    这个表包括该'类的对象'所有动态绑定的'方法及其地址'，'包括父类的方法'，
    但'一个方法只有一条记录'，子类'重写了父类方法后只会保留子类的'。
    
    当通过对象动态绑定方法的时候，只需要查找这个表就可以了，而不需要挨个查找每个父类。
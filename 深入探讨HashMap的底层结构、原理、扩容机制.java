深入探讨HashMap的底层结构、原理、扩容机制

作者：优知学院陈睿
原文地址：http://youzhixueyuan.com/the-underlying-structure-and-principle-of-hashmap.html
一、摘要

HashMap是Java程序员使用频率最高的用于映射处理的'数据类型--键值对处理'。

随着JDK（Java Developmet Kit）版本的更新，'JDK1.8对HashMap底层的实现进行了优化，例如引入红黑树的数据结构和扩容的优化等'。
本文结合JDK1.7和JDK1.8的区别，深入探讨HashMap的结构实现和功能原理。

二、简介
Java为（键值对）数据结构中的映射定义了一个接口java.util.Map，此'接口主要有四个常用的实现类'，
分别是HashMap、Hashtable、LinkedHashMap和TreeMap，类继承关系如下图所示：

下面针对各个实现类的特点做一些说明：

    (1)HashMap：它根据键的hashCode值存储数据，'大多数情况下'可以'直接定位到它的值'，因而'具有很快的访问速度'，但'遍历顺序却是不确定的'。
    HashMap最多'只允许一条记录的键为null'，允许'多条记录'的'值为null'。HashMap'非线程安全'，即多个线程在同一时刻写HashMap，可能会导致数据的不一致。
    如果需要满足线程安全，可以用 Collections的synchronizedMap方法使HashMap具有线程安全的能力，或者使用ConcurrentHashMap。

    (2)Hashtable：Hashtable是'遗留类'，很多映射的常用功能与HashMap类似，不同的是它'继承自Dictionary类'，并且'是线程安全'的，
    任一时间只有一个线程能写Hashtable，'并发性不如ConcurrentHashMap'，
    因为'ConcurrentHashMap引入了分段锁'。Hashtable'不建议在新代码中使用'，
    不需要线程安全的场合可以用HashMap替换，需要线程安全的场合可以用ConcurrentHashMap替换。

    (3)LinkedHashMap：LinkedHashMap是HashMap的一个子类，'保存了记录的插入顺序'，在用Iterator遍历LinkedHashMap时，'先得到的记录肯定是先插入的'，
    '也可以在构造时带参数，按照访问次序排序'。

    (4)TreeMap：TreeMap实现'SortedMap接口'，能够把它保存的记录'根据键排序'，'默认是按键值的升序排序'，'也可以指定排序的比较器'，
    当用Iterator遍历TreeMap时，得到的记录是排过序的。（场合：）如果使用排序的映射，建议使用TreeMap。
    在使用TreeMap时，'key必须实现Comparable接口或者在构造TreeMap传入自定义的Comparator'，
    否则会在运行时抛出java.lang.ClassCastException类型的异常。

对于上述四种Map类型的类，要求映射中'的key是不可变对象'。不可变对象是'该对象在创建后它的哈希值不会被改变'。
如果'对象的哈希值发生变化，Map对象很可能就定位不到映射的位置'了。

通过上面的比较，我们知道了HashMap是Java的Map家族中一个普通成员，'鉴于它可以满足大多数场景的使用条件'，所以是使用频度最高的一个。
下文我们主要结合源码，从'存储结构、常用方法分析、扩容以及安全性'等方面深入讲解HashMap的工作原理。

内部实现

搞清楚HashMap，首先需要知道HashMap是什么，即它的存储结构-字段；其次弄明白它能干什么，即它的功能实现-方法。下面我们针对这两个方面详细展开讲解。

三、存储结构-字段
从结构实现来讲，HashMap是:' 数组+链表+红黑树'（JDK1.8增加了红黑树部分）实现的，如下如所示。

(1)从源码可知，HashMap类中有一个非常重要的字段，就是 'Node[] table'，即哈希桶数组，明显它是一个Node的数组。我们来看Node[JDK1.8]是何物。
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash; //用来定位数组索引位置
        final K key;
        V value;
        Node<K,V> next; //链表的下一个node
        Node(int hash, K key, V value, Node<K,V> next) { ... }
        public final K getKey(){ ... }
        public final V getValue() { ... }
        public final String toString() { ... }
        public final int hashCode() { ... }
        public final V setValue(V newValue) { ... }
        public final boolean equals(Object o) { ... }
    }
Node是HashMap的一个内部类，'实现了Map.Entry接口'，本质是就是一个映射(键值对)。上图中的每个黑色圆点就是一个Node对象。

(2)HashMap就是使用哈希表来存储的。哈希表为解决冲突，'可以采用开放地址法和链地址法等来解决问题'，Java中HashMap采用了'链地址法'。
链地址法，简单来说，就是'数组加链表的结合'。在每个数组元素上都是一个链表结构，当数据被插入时,先通过对数据的哈希code和 Hash函数得到数组下标，
把数据（封装成链表节点）放在对应下标的哈希表中（数组）。例如程序执行下面代码：
map.put("优知","IT进阶站");
系统将调用”优知”这个key的hashCode()方法'得到其hashCode 值'（该方法适用于每个Java对象），然后'再通过Hash算法'的后两步运算
（高位运算和取模运算，下文有介绍）来定位该键值对的存储位置，有时两个key会定位到相同的位置，表示发生了'Hash碰撞'。
当然Hash算法计算结果越分散均匀，Hash碰撞的概率就越小，map的存取效率就会越高。

如果哈希桶数组很大，即使较差的Hash算法也会比较分散，如果哈希桶数组数组很小，即使好的Hash算法也会出现较多碰撞，所以就'需要在空间成本和时间成本之间权衡'，
其实就是在根据实际情况确定哈希桶数组的大小，并在此基础上设计好的hash算法减少Hash碰撞。那么通过什么方式来控制map使得Hash碰撞的概率又小，
哈希桶数组（Node[] table）占用空间又少呢？答案就是好的Hash算法和扩容机制。

在理解Hash和扩容流程之前，我们得先了解下HashMap的几个字段。从HashMap的默认构造函数源码可知，构造函数就是对下面几个字段进行初始化，源码如下：
 int threshold; // 所能容纳的key-value对极限 (static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16)
 final float loadFactor; // 负载因子（0.75）
 int modCount; 
 int size;
首先，Node[] table的初始化长度length(默认值是16)，Load factor为负载因子(默认值是0.75)，threshold是HashMap所能容纳的最大数据量的Node(键值对)个数。threshold = length * Load factor。也就是说，在数组定义好长度之后，负载因子越大，所能容纳的键值对个数越多。

【lincq的话】threshold并不是Hash表的长度.它的值根据负载因子和Hash表长度来确定。

结合负载因子的定义公式可知，threshold就是在此Load factor和length(数组长度)对应下允许的最大元素数目，超过这个数目就重新resize(扩容)，扩容后的HashMap容量是之前容量的两倍。默认的负载因子0.75是对空间和时间效率的一个平衡选择，建议大家不要修改，除非在时间和空间比较特殊的情况下，如果内存空间很多而又对时间效率要求很高，可以降低负载因子Load factor的值；相反，如果内存空间紧张而对时间效率要求不高，可以增加负载因子loadFactor的值，这个值可以大于1。

size这个字段其实很好理解，就是HashMap中实际存在的键值对数量。注意和table的长度length、容纳最大键值对数量threshold的区别。而modCount字段主要用来记录HashMap内部结构发生变化的次数，主要用于迭代的快速失败。强调一点，内部结构发生变化指的是结构发生变化，例如put新键值对，但是某个key对应的value值被覆盖不属于结构变化。

在HashMap中，哈希桶数组table的长度length大小必须为2的n次方(一定是合数)，这是一种非常规的设计，常规的设计是把桶的大小设计为素数。

这里存在一个问题，即使负载因子和Hash算法设计的再合理，也免不了会出现拉链过长的情况，一旦出现拉链过长，则会严重影响HashMap的性能。于是，在JDK1.8版本中，对数据结构做了进一步的优化，引入了红黑树。而当链表长度太长（默认超过8）时，链表就转换为红黑树，利用红黑树快速增删改查的特点提高HashMap的性能，其中会用到红黑树的插入、删除、查找等算法。本文不再对红黑树展开讨论，想了解更多红黑树数据结构的工作原理。

四、功能实现-方法
HashMap的内部功能实现很多，本文主要从：
1).根据key获取哈希桶数组索引位置
2).put方法的详细执行
3).扩容过程三个具有代表性的点深入展开讲解。

4.1确定哈希桶数组索引位置

不管增加、删除、查找键值对，定位到哈希桶数组的位置都是很关键的第一步。前面说过HashMap的数据结构是数组和链表的结合，
所以我们当然希望这个HashMap里面的元素位置尽量分布均匀些，尽量使得每个位置上的元素数量只有一个，那么当我们用hash算法求得这个位置的时候，
马上就可以知道对应位置的元素就是我们要的(内存直接寻址)，不用遍历链表，大大优化了查询的效率。
HashMap定位数组索引位置，直接决定了hash方法的离散性能。先看看源码的实现(方法一+方法二):

方法一：
static final int hash(Object key) { //jdk1.8 & jdk1.7
 int h;
 // h = key.hashCode() 为第一步 取hashCode值
 // h ^ (h >>> 16) 为第二步 高位参与运算 // 第三步 取模运算
 return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

方法二：
static int indexFor(int h, int length) {
//jdk1.7的源码，jdk1.8没有这个方法，但是实现原理一样的
  return h & (length-1); //第三步 取模运算
}

这里的Hash算法本质上就是三步：取key的hashCode值、高位运算、取模运算。

对于任意给定的对象，只要它的hashCode()返回值相同，那么程序调用方法一所计算得到的Hash码值总是相同的。
我们首先想到的就是把hash值对数组长度取模运算，这样一来，元素的分布相对来说是比较均匀的。
但是，模运算的消耗还是比较大的，在HashMap中是这样做的：'调用方法二来计算该对象应该保存在table数组的哪个索引处'。

【lincq】模运算（%）不如 巧妙方式模运算（&）高效

这个方法非常巧妙，它'通过h & (table.length -1)'来得到该对象的保存位——[哈希表中的下标]，
而HashMap底层数组的长度总是2的n次方，这是HashMap在速度上的优化。
'当length总是2的n次方时，h& (length-1)运算等价于对length取模，也就是h%length'，但是&比%具有更高的效率。

在JDK1.8的实现中，优化了高位运算的算法，通过hashCode()的高16位异或低16位实现的：
(h = k.hashCode()) ^ (h >>> 16)，
主要是从速度、功效、质量来考虑的，这么做可以在数组table的length比较小的时候，也能保证考虑到高低Bit都参与到Hash的计算中，同时不会有太大的开销。

下面举例说明下，n为table的长度。




4.2分析HashMap的put方法

HashMap的put方法执行过程可以通过下图来理解，自己有兴趣可以去对比源码更清楚地研究学习。

①.判断键值对数组table[i]是否为空或为null，否则执行resize()进行扩容；
②.根据键值key计算hash值得到插入的数组索引i，如果table[i]==null，直接新建节点添加，转向⑥，如果table[i]不为空，转向③；
③.判断table[i]的首个元素是否和key一样，如果相同直接覆盖value，否则转向④，这里的相同指的是hashCode以及equals；
④.判断table[i] 是否为treeNode，即table[i] 是否是红黑树，如果是红黑树，则直接在树中插入键值对，否则转向⑤；
⑤.遍历table[i]，判断链表长度是否大于8，大于8的话把链表转换为红黑树，在红黑树中执行插入操作，否则进行链表的插入操作；遍历过程中若发现key已经存在直接覆盖value即可；
⑥.插入成功后，判断实际存在的键值对数量size是否超多了最大容量threshold，如果超过，进行扩容。


4.3扩容机制

扩容(resize)就是'重新计算容量'，向HashMap对象里不停的添加元素，而HashMap对象内部的数组无法装载更多的元素时，对象就需要'扩大数组的长度'，
以便能装入更多的元素。当然Java里的数组是'无法自动扩容'的，方法是使用一个'新的数组代替已有的容量小的数组'，就像我们用一个小桶装水，
如果想装更多的水，就得换大水桶。

我们分析下resize的源码，鉴于JDK1.8融入了红黑树，较复杂，为了便于理解我们仍然使用JDK1.7的代码，好理解一些，本质上区别不大。

void resize(int newCapacity) { //传入新的容量
  Entry[] oldTable = table; //引用扩容前的Entry数组
  int oldCapacity = oldTable.length; 
  if (oldCapacity == MAXIMUM_CAPACITY) { //扩容前的数组大小如果已经达到最大(2^30)了
   threshold = Integer.MAX_VALUE; //修改阈值为int的最大值(2^31-1)，这样以后就不会扩容了
   return;
  } 
  Entry[] newTable = new Entry[newCapacity]; //初始化一个新的Entry数组
  transfer(newTable); //！！将数据转移到新的Entry数组里
  table = newTable; //HashMap的table属性引用新的Entry数组
  threshold = (int)(newCapacity * loadFactor);//修改阈值
}

这里就是使用一个容量更大的数组来代替已有的容量小的数组，transfer()方法将原有Entry数组的元素拷贝到新的Entry数组里。

void transfer(Entry[] newTable) {
 Entry[] src = table; //src引用了旧的Entry数组
 int newCapacity = newTable.length;
 for (int j = 0; j < src.length; j++) {
    //遍历旧的Entry数组
    Entry<K,V> e = src[j]; //取得旧Entry数组的每个元素
    if (e != null) {
      src[j] = null;//释放旧Entry数组的对象引用（for循环后，旧的Entry数组不再引用任何对象）
        do {
            Entry<K,V> next = e.next;
            int i = indexFor(e.hash, newCapacity); //！！重新计算每个元素在数组中的位置
            e.next = newTable[i]; //标记[1]  -- 链表头插式处理
            newTable[i] = e; //将元素放在数组上
            e = next; //访问下一个Entry链上的元素
        } while (e != null); 
} 
} 
}

newTable[i]的引用赋给了e.next，也就是'使用了单链表的头插入方式'，同一位置上新元素总会被放在链表的头部位置；
这样先放在一个索引上的元素终会被放到Entry链的尾部(如果发生了hash冲突的话），这一点和Jdk1.8有区别。在旧数组中同一条Entry链上的元素，
'通过重新计算索引位置后，有可能被放到了新数组的不同位置上'。

4.4线程安全性
在多线程使用场景中，应该尽量避免使用线程不安全的HashMap，而使用线程安全的ConcurrentHashMap。
那么为什么说HashMap是线程不安全的，下面举例子说明在并发的多线程使用场景中使用HashMap可能造成死循环。代码例子如下(便于理解，仍然使用JDK1.7的环境)：

public class HashMapInfiniteLoop { 
    private static HashMap<Integer,String> map = new HashMap<Integer,String>(2，0.75f);  //指定长度及负载因子
    public static void main(String[] args) { 
        map.put(5， "C"); 
new Thread("Thread1") { 
public void run() { 
map.put(7, "B");///////
System.out.println(map);
}; 
}.start();
new Thread("Thread2") {
public void run() { 
      map.put(3, "A); 
System.out.println(map); 
};
}.start(); 
    } 
}

其中，map初始化为一个长度为2的数组，loadFactor=0.75，threshold=2*0.75=1，也就是说当put第二个key的时候，map就需要进行resize。

[ 知道可能造成死循环就好，下面的详细过程了解一下就够了，看不懂也没有关系 ]
通过设置断点让线程1和线程2同时debug到transfer方法(3.3小节代码块)的首行。注意此时两个线程已经成功添加数据。放开thread1的断点至transfer方法的“Entry next = e.next;” 这一行；然后放开线程2的的断点，让线程2进行resize。结果如下图。


注意，Thread1的 e 指向了key(3)，而next指向了key(7)，其在线程二rehash后，指向了线程二重组后的链表。

线程一被调度回来执行，先是执行 newTalbe[i] = e， 然后是e = next，导致了e指向了key(7)，而下一次循环的next = e.next导致了next指向了key(3)。

e.next = newTable[i] 导致 key(3).next 指向了 key(7)。注意：此时的key(7).next 已经指向了key(3)， 环形链表就这样出现了。

于是，当我们用线程一调用map.get(11)时，悲剧就出现了——Infinite Loop。

4.5 JDK1.8与JDK1.7的性能对比

HashMap中，如果key经过hash算法得出的数组索引位置全部不相同，即Hash算法非常好，那样的话，getKey方法的时间复杂度就是O(1)，如果Hash算法技术的结果碰撞非常多，假如Hash算极其差，所有的Hash算法结果得出的索引位置一样，那样所有的键值对都集中到一个桶中，或者在一个链表中，或者在一个红黑树中，时间复杂度分别为O(n)和O(lgn)。 鉴于JDK1.8做了多方面的优化，总体性能优于JDK1.7，下面我们从两个方面用例子证明这一点。
五、 Hash较均匀的情况
class Key implements Comparable<Key> {
 private final int value;
 Key(int value) {
 this.value = value;
 }
 @Override
 public int compareTo(Key o) {
 return Integer.compare(this.value, o.value);
 }
 @Override
 public boolean equals(Object o) {
 if (this == o) return true;
 if (o == null || getClass() != o.getClass())
 return false;
 Key key = (Key) o;
return value == key.value;
   }
 @Override
 public int hashCode() {
 return value;
 }
}
这个类复写了equals方法，并且提供了相当好的hashCode函数，任何一个值的hashCode都不会相同，因为直接使用value当做hashcode。为了避免频繁的GC，我将不变的Key实例缓存了起来，而不是一遍一遍的创建它们。代码如下：
public class Keys {
 public static final int MAX_KEY = 10_000_000;
 private static final Key[] KEYS_CACHE = new Key[MAX_KEY];
 static {
 for (int i = 0; i < MAX_KEY; ++i) { 
 KEYS_CACHE[i] = new Key(i);
 }
   }
 public static Key of(int value) {
 return KEYS_CACHE[value];
 }
}
现在开始我们的试验，测试需要做的仅仅是，创建不同size的HashMap（1、10、100、……10000000），屏蔽了扩容的情况，代码如下：
static void test(int mapSize) {
 HashMap<Key, Integer> map = new HashMap<Key,Integer>(mapSize);
 for (int i = 0; i < mapSize; ++i) {
	 map.put(Keys.of(i), i);
 }
 long beginTime = System.nanoTime(); //获取纳秒
 for (int i = 0; i < mapSize; i++) {
 	map.get(Keys.of(i));
 }
 long endTime = System.-nanoTime();
 System.out.println(endTime - beginTime);
 }
 public static void main(String[] args) {
     for(int i=10;i<= 1000 0000;i*= 10){
test(i);
}
 }

在测试中会查找不同的值，然后度量花费的时间，为了计算getKey的平均时间，我们遍历所有的get方法，计算总的时间，除以key的数量，计算一个平均值，主要用来比较，绝对值可能会受很多环境因素的影响。结果如下：

通过观测测试结果可知，JDK1.8的性能要高于JDK1.7 15%以上，在某些size的区域上，甚至高于100%。由于Hash算法较均匀，JDK1.8引入的红黑树效果不明显，下面我们看看Hash不均匀的的情况。
六、Hash极不均匀的情况
冲突多的情况
假设我们有一个非常差的Key，它们所有的实例都返回相同的hashCode值。这是使用HashMap最坏的情况。代码修改如下：
class Key implements Comparable<Key> {
 //...
 @Override
 public int hashCode() {
 return 1;
 }
}
仍然执行main方法，得出的结果如下表所示：

从表中结果中可知，随着size的变大，JDK1.7的花费时间是增长的趋势，而JDK1.8是明显的降低趋势，并且呈现对数增长稳定。当一个链表太长的时候，HashMap会动态的将它替换成一个红黑树，这话的话会将时间复杂度从O(n)降为O(logn)。hash算法均匀和不均匀所花费的时间明显也不相同，这两种情况的相对比较，可以说明一个好的hash算法的重要性。
七、小结
(1) 扩容是一个特别耗性能的操作，所以当程序员在使用HashMap的时候，估算map的大小，初始化的时候给一个大致的数值，避免map进行频繁的扩容。
(2) 负载因子是可以修改的，也可以大于1，但是建议不要轻易修改，除非情况非常特殊。
(3) HashMap是线程不安全的，不要在并发的环境中同时操作HashMap，建议使用ConcurrentHashMap。
(4) JDK1.8引入红黑树大程度优化了HashMap的性能。
(5) HashMap的性能提升仅仅是JDK1.8的冰山一角。

【lincq】要搜索了解的内容：
modCount：内部结构发生变化
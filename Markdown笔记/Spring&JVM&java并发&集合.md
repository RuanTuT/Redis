# 1、Spring&SpringBoot

**Spring**框架的核心:IoC容器和AOP模块。基于IOC容器管理对象；通过AOP以动态无侵入的方式增强服务。

* Spring IoC 容器使用反射机制动态加载配置文件中定义的类或扫描注解，实例化 Bean 并注入依赖。

### Spring框架用到的设计模式：

* 单例模式：Bean默认为单例模式。

* 工厂模式：Spring 中的 IoC 的实现原理就是工厂模式加反射机制。

* 代理模式

  

### **BeanFactory** 和 **ApplicationContext**

BeanFactory:是Spring里面最底层的接口，低级容器。延迟加载形式来注入Bean的。即**只有在使用到某个Bean时(调用getBean())，**才对该Bean进行加载实例化。这样，我们就**不能发现一些存在的Spring的配置问题。**如果Bean的某一个属性没有注入，BeanFacotry加载后，直至 第一次使用调用getBean方法才会抛出异常。

ApplicationContext接口作为BeanFactory的**派生(子接口）**，可以称之为 **“**高级容器**”**。在容器启动时，一次性创建了所有的Bean。有利于检查所依赖属性是否注入。

BeanFactory通常以编程的方式被创建，ApplicationContext还能以声明的方式创建（xml、注解的方式）。

### @Bean 和 @Component 注解

@Bean通常与 @Configuration 配合使用。适合在需要自定义 Bean 创建逻辑时使用。

用于标注在类上，直接将该类标记为一个 Spring Bean。通过组件扫描（Component Scanning）机制，将带有注解的类自动检测并注册到容器中。

### 依赖注入的实现方式

```java
// setter注入
private BetaService betaService;   
    @Autowired
    public void setAlphaService(AlphaService alphaService) {
        this.alphaService = alphaService;
    }
```

```java
//构造函数注入
private final UserDao userDao; 
@Autowired
 public UserServiceImpl(UserDao userDao) { 
   this.userDao = userDao;
   }
```



**Setter 注入**应该被用于可选依赖项。当没有提供它们时，类应该能够正常工作。在对象被实例化之后，依赖项可以在任何时候被更改。

**构造器注入**有利于强制依赖。通过在构造函数中提供依赖，您可以确保依赖对象在被构造时已准备好被使用。在构造函数中赋值的字段也可以是final的，这使得对象是完全不可变的，或者至少可以保护其必需的字段。

**区别：**Setter注入将覆盖构造方法注入。但是构造方法注入不能覆盖setter注入值。

### 循环依赖问题

* **构造器注入无法解决循环依赖问题**：构造器注入时，Spring 容器将无法解决这种循环依赖，因为它不能创建一个未完全初始化的对象来满足另一个对象的依赖。这是因为构造器注入要求所有依赖项都必须在创建 bean 实例之前准备好。**而Setter注入可以，因为有三级缓存**。但是**当两个 Bean 存在循环依赖时，Spring 会在启动时抛出异常，明确指出依赖关系错误，而不是在运行时遇到问题。**

* **使用 @Lazy 注解**：你可以标记其中一个 bean 为懒加载（`@Lazy`），这意味着它不会在启动时立即被创建，而是在第一次需要时才被实例化。这样可以打破初始化阶段的循环依赖关系。但是，这也改变了 bean 的加载行为。

* **使用三级缓存**解决Setter注入在单例模式下的的循环依赖问题：如果允许循环依赖的话，Spring 就会将刚刚实例化完成，但是属性还没有初始化完的 Bean 对象给**提前暴露**出去，也就是向三级缓存添加一个ObjectFactory对象。当一个bean正在创建过程中,如果它被其他bean依赖,就会去三级缓存中调用 `getObject()` 方法去获取 A 的 **前期暴露的对象**（也就是之前添加的 ObjectFactory），然后将这个 `ObjectFactory` 从三级缓存中移除，并且将前期暴露对象放入到二级缓存中。`getObject()` 方法生成的是原始 Bean 对象或者代理对象，这些是已经实例化但尚未完全初始化的 Bean 的直接引用。

二级缓存够吗？

当涉及到 AOP 时，第二级缓存就显得非常重要了，因为它确保了即使在 Bean 的创建过程中有多次对早期引用的请求，也始终只返回同一个代理对象，从而避免了同一个 Bean 有多个代理对象的问题。

### **Spring AOP and AspectJ AOP** 有什么区别?

AOP代理主要分为静态代理和动态代理。静态代理的代表为AspectJ;动态代理则以SpringAOP为代表。

* 动态代理就是说AOP框架不会去修改字节码，而是每次运行时在内存中临时为方法生成一个AOP对象。
* 静态代理，就是AOP框架会在编译阶段生成AOP代理类，因此也称为编译时增强，会修改字节码。不依赖运行时反射。

### **Spring**框架中的单例**bean**是线程安全的吗?

只有有状态的单例Bean才会存在线程安全问题。

* 1、改变 bean 的作用域， 把“singleton”变更为“prototype”。
* 2、在类中定义 ThreadLocal 的成员变量，并将需要的可变成员变量保存在 ThreadLocal 中。

### 如果一个Bean没有注册成功，会报什么错？服务能够正常启动吗？

若是没有其他组件依赖这个未注册的Bean，则不会报错。反之，会抛出异常（找不到某个依赖的Bean），服务启动失败。

### Bean 的生命周期

* 实例化
* 填充属性
* BeanNameAware的setBeanName()方法
* BeanFactoryAware接口的setBeanFactory()方法
* ApplicationContextAware接口的setApplicationContext()方法
* 调用BeanPostProcessor接口的前置方法（postProcessBeforeInitialization()方法）
* InitializingBean接口的afterPropertiesSet()方法
* init-method声明了初始化方法，也会被调用
* 调用BeanPostProcessor接口的后置方法
* bean的使用
* DisposableBean接口的destory()方法销毁
* 如果bean使用了destory-method 声明销毁方法，则调用这个自定义的销毁方法

### springmvc流程

前端控制器DispatcherServlet  ----> 处理器映射器HandlerMapping ----->处理器适配器HandlerAdapter----->Handler(Controller，也叫页面控制器) ---->返回ModelAndView给DispatcherServlet ---->传给ViewReslover视图解析器 ----->对视图解析后的view进行渲染视图（模型数据model填充至视图中）----->响应用户

### Spring事务

Spring 的事务管理器是基于 **线程上下文（ThreadLocal）** 实现的。如果事务创建新线程，事务不会传播。

#### **如何让新线程支持事务传播？**

使用 **@Async** 时，事务上下文不可以传播到新线程中。如果在异步方法中显式开启事务（加上@Transactional注解）。虽然它不会共享调用者的事务上下文，但它可以自己管理独立的事务。

* @Async 是 Spring 中用于实现异步方法调用的注解。它可以让一个方法在**单独的线程**中执行，而不是阻塞调用线程。这种功能在需要提升性能或避免主线程阻塞的场景下非常有用，例如处理耗时的任务或后台操作。

* 如果直接在同一个类中调用 @Async 方法，Spring 的代理机制不会生效，方法不会异步执行。这是因为 Spring 的 AOP（面向切面编程）代理机制的原理所导致的。当类中一个方法直接调用另一个方法时，调用不经过代理对象，而是直接调用原始对象的方法。因此，AOP 的拦截逻辑（如 @Async）不会生效。
* 使用 @Async 注解的方法会在**单独的线程池**中执行。

#### 事务回滚策略

`@Transactional` 注解默认回滚策略是只有在遇到`RuntimeException`(运行时异常) 或者 `Error` 时才会回滚事务，而不会回滚 `Checked Exception`（受检查异常）,因为前者是不可预期的错误。

如果想要让所有的异常都回滚事务，可以使用如下的注解：@Transactional(rollbackFor = Exception.class) 

如果想要让某些特定的异常不回滚事务，可以使用如下的注解：@Transactional(noRollbackFor = CustomException.class)

#### java异常分类

```java
Throwable
├── Error （错误，不可处理）,JVM 层面发生的严重错误,OutOfMemoryError,StackOverflowError..
└── Exception （异常，可处理）程序逻辑或操作中发生的问题。
    ├── RuntimeException （运行时异常，非受检异常）
    └── Checked Exception （受检异常,编译器强制要求显式处理的异常,这些异常通常是由于程序员无法完全避免的外部因素导致的，比如文件读写、网络连接等。）IOException,SQLException
```



# 2、JVM

### 字节码加载机制

* 加载：双亲委派模型（先请求父加载器尝试加载，否则由当前加载）。类只有在首次使用才会加载（如实例化、访问静态字段等）。
* 链接：验证（检查class文件正确性）；准备（为静态变量分配内存并初始化默认值）；解析（符号引用替换为直接引用，有时候在初始化之后解析，为了支持Java语言的运行时绑定特性）。
* 初始化：类的静态初始化，静态变量赋值和静态代码段。保证同一个类只初始化一次。

### 类的懒加载

**懒加载**是一种延迟初始化的策略，旨在推迟资源的创建或类的加载时机，直到真正需要使用时再进行初始化。

优点：

* 加快启动速度
* 减少循环依赖的问题的出现。

### 双亲委派模型

**避免重复加载**：每次由父类加载，保证最终都是由同一个类加载器加载。

**防止核心类库被篡改：**如果 Bootstrap ClassLoader 成功加载了 java.lang.Object，则子加载器不会再加载用户自定义的 java.lang.Object。核心类库的请求都会委派给父加载器（引导类加载器）加载，防止用户篡改。

> **JVM 判定两个 Java 类是否相同的具体规则**：JVM 不仅要看类的全名是否相同，还要看加载此类的类加载器是否一样。

### 类加载器

* **`BootstrapClassLoader`(启动类加载器)**

* **`ExtensionClassLoader`(扩展类加载器)**
* **`AppClassLoader`(应用程序类加载器)**

### 对象的创建

1. 先检查类是否加载

2. 类加载并分配内存。

   分配内存方式：

   * 指针碰撞（适合堆内存规整的情况，有个分界指针，用过的内存在一边，没有用过的内存在另一边）。
   * 空闲列表。

   内存分配并发问题：

   * **CAS+失败重试**
   * **TLAB (Thread-Local Allocation Buffer)**：线程独享，堆中划分。大对象不适用，会采用第一种方法。

3. 赋初始化值零。

4. init初始化。

### 对象访问定位

栈帧中的局部变量表中的reference保存的是句柄地址，句柄在句柄池中，句柄保存一个指针，这个指针指向实际的对象或者对象类数据。

## 垃圾回收原理

堆内存的三个重要部分：

* 新生代：新生代中的对象会频繁经历垃圾回收。
* 老年代：存储生命周期较长的对象。新生代中存活的对象和大对象。
* 永久代：存储类元数据、方法区（如类信息、常量池、方法信息等），一般情况不gc，只有满了才会触发。
* 元空间：java8后用移除永久代，在本地内存用元空间替代。元空间由操作系统直接管理，垃圾回收器不会直接回收元空间，而是通过释放无用类的元数据间接回收。类的卸载通常依赖于垃圾回收器（GC）回收加载这些类的类加载器（**类加载器在堆中**）。**元空间中包括java.lang.Class 对象**。

**大对象**直接进入老年代。减少新生代的垃圾回收频率和成本。

**长期存活的对象**将进入老年代。给每个对象一个对象年龄（Age）计数器。

### 1、对象死亡的判断方法

* 引用计数法（一般不用）。效率高但**很难解决对象之间循环引用的问题。**有两个对象互相引用，但是其他对象已经不引用它们了，由于引用计数不为0，所以gc无法回收。

* 可达性分析算法。当一个对象到GC Roots（以这个对象为起点形成一个引用链）不可达时，在下一个垃圾回收周期中尝试回收该对象。经历两次标记。第一次标记会筛选是否重写finalize方法或者是否已经被调用了这个方法，若是**未重写（重写表明对象有机会被重新引用）且已经调用（每个对象的 finalize() 只能被调用一次）**，则垃圾回收器会跳过执行 finalize() 的过程，不会进入“缓刑阶段”，**直接进入回收阶段**。反之，将对象放入一个队列中，等待执行finalize()方法。如果对象在 finalize() 方法中没有完成自救，则在第二次标记时被确定为不可达，进入回收阶段。

  > GC Roots包括栈中对象，静态变量，常量，native方法中引用的对象

### 2、引用类型

* 强引用：new 创建的对象，gc不回收。
* 软引用：在内存溢出前回收。实现内存敏感缓存，有空闲内存保留缓存，否则清理掉。
* 弱引用：在下一次gc前回收。
* 虚引用：无法通过虚引用获得对象，只是用来在这个对象被gc时返回一个通知。

### 3、内存泄露的原因

* 长生命周期对象（静态变量）持有短生命周期对象的引用。
* 资源未正确关闭
* 线程池或线程未正确释放。

### 4、垃圾回收算法

* 标记-清除算法。缺点：遍历所有对象和所有的GC RooT，产生大量不连续的内存碎片。
* 复制算法。缺点：可用内存缩小为原来的一半。**新生代中使用，老年代不能使用**，老年代存活率高，有大量对象需要复制。
* 标记-整理算法。所有存活的对象压缩到一端，然后回收端边界之外的内存。解决内存碎片问题。
* 分代收集算法。新生代复制算法，老年代标记-整理。

### 5、垃圾回收器

* 新生代回收器:Serial、ParNew、Parallel Scavenge （这三个都是复制算法）
* 老年代回收器:Serial Old、Parallel Old、CMS （标记清除）。
* 整堆回收器:G1（标记整理），JDK9 之后的服务端默认收集器。

### 6、java内存分配与回收策略。

对象的内存分配通常是在 Java 堆上分配(随着虚拟机优化技术的诞生，**某些场景下也会在栈上分配**）。对象 主要分配在新生代的 Eden 区，**如果启动了本地线程缓冲，则线程优先在 TLAB 上分配**。Eden 区用于分配新创建的对象，而 TLAB 则是为每个线程在 Eden 区划分的私有区域。

* 对象优先在Eden区分配。

* 大对象直接进入老年代。减少新生代的垃圾回收频率和成本。新生代复制算法导致发生大量内存复制。

* 长期存活对象将进入老年代。给每个对象一个对象年龄（Age）计数器。经历一次MinorGC加1，年龄达到15进入老年代。

  > **Minor GC** 是指发生在新生代的 GC。当 Eden 区没有足够的空间进行分配时，虚拟机将会发起一次 Minor GC。
  >
  > **Major GC/Full GC** 是指发生在老年代的 GC



# 3、Java并发

### volatile关键字

1、保证可见性。将变量声明为 **`volatile`** ，这就指示 JVM，这个变量是共享且不稳定的，每次使用它都禁用 CPU 缓存，要到**主存中进行读取。**

2、**防止 JVM 的指令重排序。**

3、不能保证原子性。比如对同一个volatile变量递增，虽然是可见的，但是递增操作不是原子操作。



### cas

在 Java 中，实现 CAS（Compare-And-Swap, 比较并交换）操作的一个关键类是`Unsafe`。 CAS 方法是`native`方法。直接调用底层的硬件指令来实现原子操作。

`java.util.concurrent.atomic` 包提供了一些用于原子操作的类。这些类利用底层的原子指令，确保在多线程环境下的操作是线程安全的。

缺点：

* ABA问题：准备赋值时检查是否是A值，若是A值，不代表没有变过，有可能先是变B再变A；

* 自旋问题：循环开销大；
* 只能保证一个共享变量的原子操作。

## 锁

* **公平锁** : 锁被释放之后，先申请的线程先得到锁。**性能较差。**
* **非公平锁**：锁被释放之后，后申请的线程可能会先获取到锁，是随机或者按照其他优先级排序的。存在某个线程永远获取不到锁的现象。

### synchronized关键字

能同步方法和同步代码块。同步方法时synchronized修饰静态方法和修饰实例方法是不互斥的。一个锁当前对象实例，一个锁当前类。

>  synchronized 修饰的实例方法会锁定当前对象实例（this），只有持有该实例锁的线程才能执行此方法。**其他线程无法访问同一个实例中任何被 synchronized 修饰的方法，直到当前线程释放锁。**

* 是可重入锁。
* 只能是非公平锁。
* 不可中断锁。
* 不需要手动释放锁。

锁升级：

之前是重量级锁（悲观锁），需要线程切换状态，消耗系统资源。后来JVM对synchronized关键字优化，分为无锁、偏向锁、轻量级锁、重量级锁 状态。

### lock实现类的锁

JDK 提供的所有现成的 `Lock` 实现类，包括 `synchronized` 关键字锁都是可重入的。

####  ReentrantLock

* 可重入锁

*  默认使用**非公平锁**，也可以通过构造器来显式的指定使用公平锁。
* 可中断锁。获取锁的过程中可以被中断，不需要一直等到获取锁之后 才能进行其他逻辑处理。
* 需要手动开启释放锁：lock，unlock
* 只能给代码块加锁。



##  ThreadLocal类

内存泄露问题：

`ThreadLocalMap` 中使用的 key 为 `ThreadLocal` 的弱引用，在外部没有强引用时会被回收。而 value 是强引用。

在垃圾回收的时候，key 会被清理掉，而 value 不会被清理掉。使用完 `ThreadLocal`方法后最好手动调用`remove()`方法。	

```java
public void example() {
    ThreadLocal<String> threadLocal = new ThreadLocal<>();
    threadLocal.set("value");
}
在这个方法执行结束后，threadLocal 的引用超出了作用域，程序中没有任何变量指向这个 ThreadLocal 实例。此时，它就没有了外部强引用，可以被垃圾回收。
```

## 线程池

### 创建线程池方式：

* 使用Executors工具类创建线程池，提供一系列静态方法创建各种线程池。返回的线程池都实现了ExecutorService接口，ExecutorService 接口继承了 Executor 接口。（不推荐使用）`ExecutorService executorService = Executors.newSingleThreadExecutor();`
* 使用ThreadPoolExecutor构造函数创建自定义线程池。`ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(2, 5, 200, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(5));`

### Submit()和execute()方法区别

* 接收参数：execute()只能执行 Runnable 类型的任务。submit()可以执行 Runnable 和 Callable 类型的任务
* 返回值：submit()方法可以获取异步计算结果 Future 对象，而execute()没有
* 异常处理：submit在执行过程中与execute不一样，不会抛出异常而是把异常保存在成员变量中(`Future` 对象)，在FutureTask.get阻塞获取的时候再把异常抛出来。

### runnable、callable

Callalbe接口支持返回执行结果，需要调用FutureTask.get()得到，此方法会阻塞主进程的继续往下执行，如果不调用不会阻塞。

### futuretask

* `FutureTask` 提供了 `Future` 接口的基本实现，常用来封装 `Callable` 和 `Runnable`，具有取消任务、查看任务是否执行完成以及获取任务执行结果的方法。`FutureTask` 有两个构造函数，可传入 `Callable` 或者 `Runnable` 对象。实际上，传入 `Runnable` 对象也会在方法内部转换为`Callable` 对象。

* `ExecutorService.submit()` 方法返回的其实就是 `Future` 的实现类 `FutureTask` 。
* `FutureTask` 不光实现了 `Future`接口，还实现了`Runnable` 接口，因此可以作为任务直接被线程执行。
* FutureTask.get方法会堵塞主进程。`CompletableFuture` 类可以通过回调机制解决`Future` 的这些缺陷。

### shutdown()`VS`shutdownNow()

- **`shutdown（）`** :关闭线程池，线程池的状态变为 `SHUTDOWN`。线程池不再接受新任务了，但是队列里的任务得执行完毕。
- **`shutdownNow（）`** :关闭线程池，线程池的状态变为 `STOP`。线程池会终止当前正在运行的任务，并停止处理排队的任务并返回正在等待执行的 List。

### isTerminated()` VS `isShutdown()

- **`isShutDown`** 当调用 `shutdown()` 方法后返回为 true。
- **`isTerminated`** 当调用 `shutdown()` 方法后，并且所有提交的任务完成后返回为 true



# 4、集合

## List接口

### ArrayList

扩容机制：首先创建一个空数组**elementData**，第一次插入数据时直接**扩充至10**，然后 如果**elementData**的长度不足，就扩充至1.5倍，如果扩充完还不够，就使用需要的长度作为**elementData**的长度。

remove()方法：根据索引删除指定位置的元素，此时会把指定下标到数组末尾的元素挨个向前移动一个单位，并且会把数组最后一个元素设置为null，这样是为了方便之后将整个数组不被使用时，会被GC，可以作为小的技巧使用。

线程安全:ArrayList 和 LinkedList 都是不同步的，也就是不保证线程安全;Vector 使用了 synchronized 来实现线程同步，是线程安全的。

### **Java**集合的快速失败机制 **“fail-fast”**?

ArrayList的Iterator中有一个 expectedModCount 变量，该变量会 初始化和 modCount 相等，每当迭代器使用hashNext()/next()遍历下一个元素之前，都会检测modCount的值和 expectedmodCount的值是否相等，如果集合进行增删操作， modCount 变量就会改变，就会造成 expectedModCount!=modCount ，此时就会抛出ConcurrentModificationException异常

## set接口

HashSet(无序，唯一):基于 HashMap 实现，底层采用 HashMap 的key来保存元素 LinkedHashSet:LinkedHashSet 继承于 HashSet，并且其内部是通过 LinkedHashMap 来实现的。 TreeSet(有序，唯一):红黑树(自平衡的排序二叉树)

## Map接口

### HashMap

jdk1.8之前：采用拉链法。头插法。

jdk1.8之后：尾插法。**当链表长度大于阈值(默认为8)，但是数组长度小于64**时会首先回扩容，否则将链表转换为红黑树，减少搜索时间。

优化了：1、扩容优化。2、引入红黑树避免链表过长影响查询效率。3、解决多线程环境的头插法破坏链表结构。尤其在扩容时，容易形成环形链表。缺点：线程不安全。

#### 为什么**HashMap**中**String**、**Integer**这样的包装类适合作为**key**?

* 都是final类型，即不可变性。
* 内部已重写了equals()、hashCode()等方法，不容易出现Hash值计算错误的情况。

#### 如果使用**Object**作为**HashMap**的**Key**，应该怎么办呢?

* 重写 hashCode()。计算数据的存储位置

*  重写 equals() 方法。目的是为了保证**key**在哈希表中的唯一性



### TreeMap

* 基于红黑树的一种提供顺序访问的 Map，键有序。
* 提供方法如 firstKey(), lastKey(), subMap(), 便于范围查询。
* get、put、remove 之类操作都是 O(logn) 的时间复杂度

### ConcurrentHashMap

**JDK1.7**时：对整个桶数组进行了分割分段(Segment)，**每一个Segment都类似一个HashMap，相当于HashMap被分段。**然后在每一个分段上都用lock锁进行保护，相对于 HashTable的synchronized锁的粒度更精细了一些，并发性能更好。**对段加锁。**

在**JDK1.8**中，放弃了**Segment**臃肿的设计，取而代之的是采用**Node + CAS + Synchronized**来保证并发安 全，synchronized只锁定当前链表或红黑二叉树的首节点，这样**只要hash不冲突，就不会产生并发**。**对链表/红黑树加锁。**ConcurrentHashMap 在必要时使用 synchronized代替CAS，避免过度自旋。
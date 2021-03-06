/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.channel.group.ChannelGroup;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认情况下每一个客户端发起连接，都会重新创建一个pipeline，而pipeline中会初始化不同的Handler(同一个Handler不被多次添加到Pipeline中)，因此对每一个Connection而言
 * handler的实例都是不一样的。
 * 通过在Handler上添加 {@link Sharable} 可以改变这个行为，这样Handler可以多次添加到pipeline中共享
 *
 * ChannelHandler是事件处理的核心接口，所处理的内容，除了业务逻辑也包括通信底层的的链接创建，解码等操作
 *
 * Handles or intercepts a {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 *
 * ChannelHandler处理ChannelEvent，并发送一个ChannelEvent给在ChannelPipeline中的下一个Handler
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide any method.  To handle a
 * {@link ChannelEvent} you need to implement its sub-interfaces.  There are
 * two sub-interfaces which handles a received event, one for upstream events
 * and the other for downstream events:
 * <ul>
 * <li>{@link ChannelUpstreamHandler} handles and intercepts an upstream {@link ChannelEvent}.</li>
 * <li>{@link ChannelDownstreamHandler} handles and intercepts a downstream {@link ChannelEvent}.</li>
 * </ul>
 *
 * ChannelHandler的具体行为需要在具体的接口中进行定义，
 * 例如和Socket中的InputStream和OutPutStream一样，也区分读取和写入两个方向，这个在Netty4中做了调整
 *
 * You will also find more detailed explanation from the documentation of
 * each sub-interface on how an event is interpreted when it goes upstream and
 * downstream respectively.
 *
 * 具体的接口中，对ChannelEvent事件的处理方式会有区别
 *
 * 例如在输入和输入两种模式下，事件会有不同的解释，主要是对消息的读写事件不同，即 downstream event  或者是 upstream event
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (attachment) which is specific to the handler.
 *
 * 一般情况这个ChannelHandlerContext 是 {@link DefaultChannelPipeline.DefaultChannelHandlerContext} ,
 * 在其中定义了整个Netty事件流串联的过程，包括filter/intercepting模式的实现
 * 也可以通过ctx来动态改变Pipeline增加一些Handler或者做其他的一些处理，更多的功能和实现可以研究 DefaultChannelHandlerContext
 *
 * <h3>State management</h3>
 *
 * Channel的状态管理
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 *     推荐使用成员变量
 *
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>loggedIn = true;</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 *
 * 在这边必须要做到每一个连接对应到一个Handler实例，不能在多个Connection中共享Handler
 *
 * <pre>
 * // Create a new handler instance per channel.
 * // See {@link Bootstrap#setPipelineFactory(ChannelPipelineFactory)}.
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>new DataServerHandler()</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>Using an attachment</h4>
 * 使用附件的形式，
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use an <em>attachment</em> which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>ctx.setAttachment(true)</b>;
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(ctx.getAttachment())</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is stored as an attachment, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>Using a {@link ChannelLocal}</h4>
 *
 * 使用Channel作用域的变量
 * If you have a state variable which needs to be accessed either from other
 * handlers or outside handlers, you can use {@link ChannelLocal}:
 * <pre>
 * public final class DataServerState {
 *
 *     <b>public static final {@link ChannelLocal}&lt;Boolean&gt; loggedIn = new {@link ChannelLocal}&lt;&gt;() {
 *         protected Boolean initialValue(Channel channel) {
 *             return false;
 *         }
 *     }</b>
 *     ...
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         Channel ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>DataServerState.loggedIn.set(ch, true);</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *                 ctx.getChannel().write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 *
 * // Print the remote addresses of the authenticated clients:
 * {@link ChannelGroup} allClientChannels = ...;
 * for ({@link Channel} ch: allClientChannels) {
 *     if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *         System.out.println(ch.getRemoteAddress());
 *     }
 * }
 * </pre>
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * In the examples above which used an attachment or a {@link ChannelLocal},
 * you might have noticed the {@code @Sharable} annotation.
 *
 * 使用附件和ChannelLocal的方式，需要注意@Shareable的使用
 *
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelEvent} and {@link ChannelPipeline} to find
 * out what a upstream event and a downstream event are, what fundamental
 * differences they have, and how they flow in a pipeline.
 *
 * @apiviz.landmark
 * @apiviz.exclude ^org\.jboss\.netty\.handler\..*$
 */
public interface ChannelHandler {

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}

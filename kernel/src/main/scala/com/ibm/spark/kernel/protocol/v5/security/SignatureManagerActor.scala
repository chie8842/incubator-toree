package com.ibm.spark.kernel.protocol.v5.security

import akka.actor.{Props, ActorRef, Actor}
import akka.util.Timeout
import com.ibm.spark.kernel.protocol.v5.KernelMessage
import com.ibm.spark.security.{Hmac, HmacAlgorithm}
import com.ibm.spark.utils.LogLike

import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe

class SignatureManagerActor(
  key: String, scheme: String
) extends Actor with LogLike {
  private val hmac = Hmac(key, HmacAlgorithm(scheme))

  def this(key: String) = this(key, HmacAlgorithm.SHA256.toString)

  // NOTE: Required to provide the execution context for futures with akka
  import context._

  // NOTE: Required for ask (?) to function... maybe can define elsewhere?
  implicit val timeout = Timeout(5.seconds)

  //
  // List of child actors that the signature manager contains
  //
  private var signatureChecker: ActorRef = _
  private var signatureProducer: ActorRef = _

  /**
   * Initializes all child actors performing tasks for the interpreter.
   */
  override def preStart() = {
    signatureChecker = context.actorOf(
      Props(classOf[SignatureCheckerActor], hmac),
      name = SignatureManagerChildActorType.SignatureChecker.toString
    )
    signatureProducer = context.actorOf(
      Props(classOf[SignatureProducerActor], hmac),
      name = SignatureManagerChildActorType.SignatureProducer.toString
    )
  }

  override def receive: Receive = {
    // Check blob strings for matching digest
    case (signature: String, blob: Seq[_]) =>
      (signatureChecker ? ((signature, blob))) pipeTo sender

    case message: KernelMessage =>
      // TODO: Proper error handling for possible exception from mapTo
      (signatureProducer ? message).mapTo[String].map(
        result => message.copy(signature = result)
      ) pipeTo sender
  }
}

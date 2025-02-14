package com.itv.scalapact.circe14

import com.itv.scalapact.shared.Notice._
import com.itv.scalapact.shared._
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, parser}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.syntax._

import scala.util.{Failure, Success, Try}

object PactImplicits {

  implicit val pactActorDecoder: Codec[PactActor] = Codec.forProduct1("name")(PactActor.apply)(_.name)

  implicit val matchingRuleDecoder: Codec[MatchingRule] = deriveCodec

  implicit val interactionRequestDecoder: Decoder[InteractionRequest] = Decoder.instance { cur =>
    for {
      method  <- cur.get[Option[String]]("method")
      path    <- cur.get[Option[String]]("path")
      query   <- cur.get[Option[String]]("query")
      headers <- cur.get[Option[Map[String, String]]]("headers")
      body = cur.downField("body").focus.flatMap {
        case j if j.isString => j.asString
        case j               => Some(j.dropNullValues.spaces2)
      }
      matchingRules <- cur.get[Option[Map[String, MatchingRule]]]("matchingRules")
    } yield InteractionRequest(method, path, query, headers, body, matchingRules)
  }

  implicit val interactionRequestEncoder: Encoder[InteractionRequest] = Encoder.instance { r =>
    val encodedBody: Option[Json] = r.body.map(b => parser.parse(b).toOption.getOrElse(Json.fromString(b)))
    Json.obj(
      "method"        -> r.method.asJson,
      "path"          -> r.path.asJson,
      "query"         -> r.query.asJson,
      "headers"       -> r.headers.asJson,
      "body"          -> encodedBody.asJson,
      "matchingRules" -> r.matchingRules.asJson
    )
  }

  implicit val interactionResponseDecoder: Decoder[InteractionResponse] = Decoder.instance { cur =>
    for {
      status  <- cur.get[Option[Int]]("status")
      headers <- cur.get[Option[Map[String, String]]]("headers")
      body = cur.downField("body").focus.flatMap {
        case j if j.isString => j.asString
        case j               => Some(j.dropNullValues.spaces2)
      }
      matchingRules <- cur.get[Option[Map[String, MatchingRule]]]("matchingRules")
    } yield InteractionResponse(status, headers, body, matchingRules)
  }

  implicit val interactionResponseEncoder: Encoder[InteractionResponse] = Encoder.instance { r =>
    val encodedBody: Option[Json] = r.body.map(b => parser.parse(b).toOption.getOrElse(Json.fromString(b)))
    Json.obj(
      "status"        -> r.status.asJson,
      "headers"       -> r.headers.asJson,
      "body"          -> encodedBody.asJson,
      "matchingRules" -> r.matchingRules.asJson
    )
  }

  implicit val interactionDecoder: Decoder[Interaction] = Decoder.instance { cur =>
    for {
      providerState  <- cur.get[Option[String]]("providerState")
      provider_state <- cur.get[Option[String]]("provider_state")
      ps = providerState.orElse(provider_state).filterNot(_.isEmpty)
      description <- cur.get[String]("description")
      request     <- cur.get[InteractionRequest]("request")
      response    <- cur.get[InteractionResponse]("response")
    } yield Interaction(ps, description, request, response)
  }

  implicit val interactionEncoder: Encoder[Interaction] = deriveEncoder

  implicit val linkValueDecoder: Codec[LinkValues] = deriveCodec

  implicit val versionMetaDataDecoder: Codec[VersionMetaData] =
    Codec.forProduct1("version")(VersionMetaData.apply)(_.version)

  implicit val pactMetaDataDecoder: Codec[PactMetaData] = deriveCodec

  implicit val scalaPactDecoder: Decoder[Pact] = Decoder.instance { cur =>
    for {
      provider     <- cur.get[PactActor]("provider")
      consumer     <- cur.get[PactActor]("consumer")
      interactions <- cur.get[List[Interaction]]("interactions")
      _links       <- cur.downField("_links").downField("curies").delete.as[Option[Links]]
      metadata     <- cur.get[Option[PactMetaData]]("metadata")
    } yield Pact(provider, consumer, interactions, _links, metadata)
  }

  implicit val jvmPactDecoder: Decoder[JvmPact] = Decoder.instance { cur =>
    for {
      consumer <- cur.get[PactActor]("consumer")
      provider <- cur.get[PactActor]("provider")
      body = cur.value.noSpaces
    } yield JvmPact(consumer, provider, body)
  }

  implicit val scalaPactEncoder: Encoder[Pact] = deriveEncoder

  implicit val jvmPactEncoder: Encoder[JvmPact] = Encoder.instance { jvmPact =>
    io.circe.parser.parse(jvmPact.rawContents) match {
      case Right(value) => value
      case Left(error)  => throw new Exception(s"Generated pact is not valid json: ${error}")
    }
  }

  implicit val halIndexDecoder: Decoder[HALIndex] = Decoder.instance { cur =>
    cur.downField("_links").downField("curies").delete.as[Links].map(HALIndex.apply)
  }

  implicit val embeddedPactForVerificationDecoder: Decoder[PactForVerification] = deriveDecoder
  implicit val embeddedPactsForVerificationDecoder: Decoder[EmbeddedPactsForVerification] = Decoder.instance { cur =>
    cur.get[List[PactForVerification]]("pacts").map(EmbeddedPactsForVerification.apply)
  }

  implicit val pendingStateNoticeDecoder: Decoder[Notice] = Decoder.instance { cur =>
    lazy val pattern = "after_verification:success_(true|false)_published_(true|false)".r
    cur.get[String](s"text").flatMap { text =>
      cur.get[Option[String]]("when").flatMap {
        case Some("before_verification") => Right(BeforeVerificationNotice(text))
        case Some(pattern(success, published)) =>
          (for {
            suc <- Try(success.toBoolean)
            pub <- Try(published.toBoolean)
          } yield AfterVerificationNotice(text, suc, pub)) match {
            case Failure(err)   => Left(DecodingFailure.fromThrowable(err, cur.history))
            case Success(value) => Right(value)
          }
        case _ => Right(SimpleNotice(text))
      }
    }
  }

  implicit val verificationPropertiesDecoder: Decoder[VerificationProperties] = Decoder.instance { cur =>
    for {
      pending <- cur.getOrElse[Boolean]("pending")(false)
      notices <- cur.get[List[Notice]]("notices")
    } yield VerificationProperties(pending, notices)
  }

  implicit val pactsForVerificationDecoder: Decoder[PactsForVerificationResponse] = deriveDecoder

  implicit val consumerVersionSelectorEncoder: Encoder[ConsumerVersionSelector]         = deriveEncoder
  implicit val pactsForVerificationRequestEncoder: Encoder[PactsForVerificationRequest] = deriveEncoder
}

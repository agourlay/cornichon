package com.github.agourlay.cornichon.http

import scala.collection.immutable.ArraySeq

case class HttpResponse(
    status: Short,
    headers: ArraySeq[(String, String)],
    body: String
)

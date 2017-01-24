type A
type B


import cats.syntax.either._

def a: Either[A, B]
def b(b: B): Either[A, B]


val c: Either[A, B] = a.flatMap(b)
package org.reekwest.http.contract.module

import org.reekwest.http.core.HttpHandler
import org.reekwest.http.core.Method
import org.reekwest.http.core.Request
import org.reekwest.http.core.then
import org.reekwest.http.lens.LensFailure
import org.reekwest.http.lens.Path
import org.reekwest.http.lens.PathLens

class ServerRoute internal constructor(private val pathBinder: PathBinder, private val toHandler: (ExtractedParts) -> HttpHandler) {
    fun router(moduleRoot: BasePath): Router = pathBinder.toRouter(moduleRoot, toHandler)

    fun describeFor(basePath: BasePath): String = pathBinder.describe(basePath)
}

abstract class PathBinder internal constructor(internal val core: Core, private vararg val pathLenses: PathLens<*>) {
    abstract infix operator fun <T> div(next: PathLens<T>): PathBinder

    open infix operator fun div(next: String) = div(Path.fixed(next))

    internal fun toRouter(moduleRoot: BasePath, toHandler: (ExtractedParts) -> HttpHandler): Router =
        {
            if (core.matches(moduleRoot, it)) {
                try {
                    it.extract(moduleRoot, pathLenses.toList())?.let { core.route.validationFilter.then(toHandler(it)) }
                } catch (e: LensFailure) {
                    null
                }
            } else null
        }

    fun describe(basePath: BasePath) = "${core.pathFn(basePath)}/${pathLenses.joinToString("/")}"

    companion object {
        internal data class Core(val route: Route, val method: Method, val pathFn: (BasePath) -> BasePath) {
            infix operator fun div(next: String) = copy(pathFn = { pathFn(it) / next })
            fun matches(moduleRoot: BasePath, request: Request) =
                request.method == method && request.basePath().startsWith(pathFn(moduleRoot))
        }
    }
}

class PathBinder0 internal constructor(core: Core) : PathBinder(core) {

    override infix operator fun div(next: String) = PathBinder0(core / next)

    override infix operator fun <T> div(next: PathLens<T>) = PathBinder1(core, next)

    infix fun bind(handler: HttpHandler) = ServerRoute(this, { handler })
}

class PathBinder1<out A> internal constructor(core: Core,
                                              private val psA: PathLens<A>) : PathBinder(core, psA) {

    override infix operator fun div(next: String) = div(Path.fixed(next))

    override infix operator fun <T> div(next: PathLens<T>) = PathBinder2(core, psA, next)

    infix fun bind(fn: (A) -> HttpHandler) = ServerRoute(this, { parts -> fn(parts[psA]) })
}

class PathBinder2<out A, out B> internal constructor(core: Core,
                                                     private val psA: PathLens<A>,
                                                     private val psB: PathLens<B>) : PathBinder(core, psA, psB) {
    override infix operator fun div(next: String) = div(Path.fixed(next))

    override fun <T> div(next: PathLens<T>) = PathBinder3(core, psA, psB, next)

    infix fun bind(fn: (A, B) -> HttpHandler) = ServerRoute(this, { parts -> fn(parts[psA], parts[psB]) })
}

class PathBinder3<out A, out B, out C> internal constructor(core: Core,
                                                            private val psA: PathLens<A>,
                                                            private val psB: PathLens<B>,
                                                            private val psC: PathLens<C>) : PathBinder(core, psA, psB, psC) {
    override infix operator fun div(next: String) = div(Path.fixed(next))

    override fun <T> div(next: PathLens<T>) = PathBinder4(core, psA, psB, psC, next)

    infix fun bind(fn: (A, B, C) -> HttpHandler) = ServerRoute(this, { parts -> fn(parts[psA], parts[psB], parts[psC]) })
}

class PathBinder4<out A, out B, out C, out D> internal constructor(core: Core,
                                                                   private val psA: PathLens<A>,
                                                                   private val psB: PathLens<B>,
                                                                   private val psC: PathLens<C>,
                                                                   private val psD: PathLens<D>) : PathBinder(core, psA, psB, psC, psD) {
    override infix operator fun div(next: String) = div(Path.fixed(next))

    override fun <T> div(next: PathLens<T>) = throw UnsupportedOperationException("No support for longer paths!")

    infix fun bind(fn: (A, B, C, D) -> HttpHandler) = ServerRoute(this, { parts -> fn(parts[psA], parts[psB], parts[psC], parts[psD]) })
}

internal class ExtractedParts(private val mapping: Map<PathLens<*>, *>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(lens: PathLens<T>): T = mapping[lens] as T
}

private operator fun <T> BasePath.invoke(index: Int, fn: (String) -> T): T? = toList().let { if (it.size > index) fn(it[index]) else null }

private fun Request.extract(moduleRoot: BasePath, lenses: List<PathLens<*>>): ExtractedParts? =
    without(moduleRoot).let {
        path ->
        if (path.toList().size == lenses.size) ExtractedParts(lenses.mapIndexed { index, lens -> lens to path(index, lens) }.toMap()) else null
    }
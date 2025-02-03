import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.khronos.webgl.WebGLRenderingContext as GL
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import vision.gears.webglmath.*
import kotlin.js.Date
import kotlin.math.*
import kotlin.random.*

class Scene (
  val gl : WebGL2RenderingContext,
  val overlay : HTMLDivElement,
  val canvas : HTMLCanvasElement) : UniformProvider("scene") {

  lateinit var defaultFramebuffer: DefaultFramebuffer
  var frameCount = 0

  val vsQuad = Shader(gl, GL.VERTEX_SHADER, "quad-vs.glsl")
  val fsBackground = Shader(gl, GL.FRAGMENT_SHADER, "background-fs.glsl")
  val fsTextured = Shader(gl, GL.FRAGMENT_SHADER, "textured-fs.glsl")
  val vstextured = Shader(gl, GL.VERTEX_SHADER, "textured-vs.glsl")

  val fsShow = Shader(gl, GL.FRAGMENT_SHADER, "show-fs.glsl")

  val fsuj = Shader(gl, GL.FRAGMENT_SHADER, "uj-fs.glsl")

  val texturedProgram = Program(gl, vstextured, fsTextured)


  val quadGeometry = TexturedQuadGeometry(gl)
  val flipQuadGeometry = FlipQuadGeometry(gl)

  val framebufferCube = FramebufferCube(gl, 1, 512)

  val showMesh = Mesh(Material(Program(gl, vsQuad, fsShow)), flipQuadGeometry)

  val backgroundProgram = Program(gl, vsQuad, fsBackground)
  val backgroundMaterial = Material(backgroundProgram)
  val ujProgram = Program(gl, vsQuad, fsuj)
  val ujmaterial = Material(ujProgram)

  val skyCubeTexture = TextureCube(
    gl,
    "media/px.png", "media/nx.png",
    "media/py.png", "media/ny.png",
    "media/pz.png", "media/nz.png"
  )

  init {
    backgroundMaterial["envTexture"]?.set(skyCubeTexture)
  }

  val backgroundMesh = Mesh(backgroundMaterial, quadGeometry)


  val jsonLoader = JsonLoader()
  val exhibitGeometries = jsonLoader.loadMeshes(gl, "media/json/chevy/chassis.json", Material(texturedProgram).apply {
    this["colorTexture"]?.set(
      Texture2D(gl, "media/json/chevy/chevy.png")
    )
  })
  val wheelgeometries = jsonLoader.loadMeshes(gl, "media/json/chevy/wheel.json", Material(texturedProgram).apply {
    this["colorTexture"]?.set(
      Texture2D(gl, "media/json/chevy/chevy.png")
    )
  })
  val sphereGeometry = jsonLoader.loadMeshes(gl, "media/sphere.json", Material(texturedProgram).apply {
    this["colorTexture"]?.set(
      Texture2D(gl, "media/asteroid.png")
    )
  })

  /////////////////////////////////////
  val imageTexture = Texture2D(gl, "media/road.jpg")

  val texturedMaterial = Material(texturedProgram).apply {
    this["colorTexture"]?.set(imageTexture)
  }
  val texturedQuad = Mesh(texturedMaterial, quadGeometry)

  /////////////////////////////////////
  val vsTransform = Shader(gl, GL.VERTEX_SHADER, "transform-vs.glsl")
  val fsPhong = Shader(gl, GL.FRAGMENT_SHADER, "phong-fs.glsl")
  val phongProgram = Program(gl, vsTransform, fsPhong)
  val phongMaterial = Material(phongProgram).apply {
    this["kd"]?.set(Vec3(0.6f, 0.2f, 0.1f))
    this["ks"]?.set(Vec3(1f, 1f, 1f))
    this["shininess"]?.set(15f)
  }

  val fsEnvmapped = Shader(gl, GL.FRAGMENT_SHADER, "envmapped-fs.glsl")
  val envmappedProgram = Program(gl, vsTransform, fsEnvmapped)
  val envmappedMaterial = Material(envmappedProgram).apply {
    this["kr"]?.set(Vec3(0.5f, 0.5f, 0.2f))
    this["environment"]?.set(
      framebufferCube.targets[0]
    )

  }

  fun randomFloat(min: Float, max: Float): Float {
    return Random.nextFloat() * (max - min) + min
  }

  val gameObjects = ArrayList<GameObject>()
  val spheres = ArrayList<GameObject>()
  val roads = ArrayList<GameObject>()

  init {
    gameObjects += GameObject(*exhibitGeometries)

    val wheelOffsetX = 7f
    val wheelOffsetZ = 10f
    val wheelY = -3f

    for (i in 0 until 4) {
      gameObjects += GameObject(*wheelgeometries).apply {
        when (i) {
          0 -> {
            position.set(-wheelOffsetX, wheelY, -wheelOffsetZ - 1f) // hatso
            parent = gameObjects[0]
          }

          1 -> {
            position.set(wheelOffsetX, wheelY, -wheelOffsetZ - 1f) // hatso
            parent = gameObjects[0]
          }

          2 -> {
            position.set(-wheelOffsetX, wheelY, wheelOffsetZ + 4f) // elso
            parent = gameObjects[0]
          }

          3 -> {
            position.set(wheelOffsetX, wheelY, wheelOffsetZ + 4f) // elso
            parent = gameObjects[0]
          }
        }
      }
    }
    for (i in 0 until 20) {
      spheres += GameObject(*sphereGeometry).apply {
        position.set(
          randomFloat(-100f, 100f),
          0f,
          randomFloat(25f, 2000f)
        )
        scale.set(5f, 5f, 5f)
      }
    }
    for (i in 0 until 20) {
      roads += GameObject(texturedQuad).apply {
        position.set(0f, 44f, 2000f - (0f + i * 200f))
        scale.set(100f, 100f, 100f)
        pitch = PI.toFloat() / 2.0f
        roll = PI.toFloat() / 2.0f
      }
    }
  }

  val lights = Array<Light>(1) { Light(it) }

  init {
    lights[0].position.set(1.0f, 1.0f, 1.0f, 0.0f).normalize()
    lights[0].powerDensity.set(5.0f, 5.0f, 5.0f)
  }

  val camera = PerspectiveCamera()

  val timeAtFirstFrame = Date().getTime()
  var timeAtLastFrame = timeAtFirstFrame

  init {
    gl.enable(GL.DEPTH_TEST)
  }

  fun resize(gl: WebGL2RenderingContext, canvas: HTMLCanvasElement) {
    gl.viewport(0, 0, canvas.width, canvas.height)
    camera.setAspectRatio(canvas.width.toFloat() / canvas.height.toFloat())

    defaultFramebuffer = DefaultFramebuffer(canvas.width, canvas.height)
  }

  fun detectCollision(car: GameObject, obj2: GameObject): Boolean {
    val minbound = Vec3(car.position.x - 8f, car.position.y - 2.5f, car.position.z - 18f)
    val maxbound = Vec3(car.position.x + 8f, car.position.y + 2.5f, car.position.z + 18f)

    val sphereCenter = obj2.position
    val sphereRadius = obj2.scale.x / 2.0f

    val closestPoint = Vec3(
      max(minbound.x, min(sphereCenter.x, maxbound.x)),
      max(minbound.y, min(sphereCenter.y, maxbound.y)),
      max(minbound.z, min(sphereCenter.z, maxbound.z))
    )

    val distanceSquared = (closestPoint - sphereCenter).lengthSquared()

    return distanceSquared <= (sphereRadius * sphereRadius)
  }

  fun applyPushForce(car: GameObject, sphere: GameObject, forceMagnitude: Float) {
    val direction = (sphere.position - car.position).normalize()

    sphere.position = direction * forceMagnitude + sphere.position

    pushDirections[sphere] = direction
  }

  fun continueMovingSpheres(dt: Float) {
    for (sphere in pushDirections.keys) {
      val direction = pushDirections[sphere]
      if (direction != null) {
        sphere.position = direction * dt * 17f + sphere.position
        sphere.pitch += dt * 6.0f * direction.z
      }
    }
  }

  val pushDirections = mutableMapOf<GameObject, Vec3>()

  var cameraVelocity = Vec3(0.0f, 0.0f, 0.0f)

  fun updateHeliCamera(avatar: GameObject, camera: PerspectiveCamera, dt: Float) {
    val cosYaw = cos(-avatar.yaw)
    val sinYaw = sin(-avatar.yaw)

    val offsetX = 0f * cosYaw - (-50f) * sinYaw
    val offsetZ = 0f * sinYaw + (-50f) * cosYaw
    val offset = Vec3(offsetX, 15f, offsetZ)
    val targetPosition = avatar.position + offset

    val displacement = targetPosition - camera.position

    val springStrength = 220f
    val springForce = displacement * springStrength

    val dampingFactor = 100f
    val dampingForce = cameraVelocity * dampingFactor

    cameraVelocity = ((springForce - dampingForce) * dt) + cameraVelocity

    camera.position += cameraVelocity * dt

    camera.lookAt(avatar.position)
  }

  fun calculateCarDirection(yaw: Float): Boolean {
    val forward = Vec3(sin(yaw), 0.0f, cos(yaw))
    return forward.normalize().z > 0
  }
  var isControllable = true
  @Suppress("UNUSED_PARAMETER")
  fun update(gl: WebGL2RenderingContext, keysPressed: Set<String>, timeStamp: Double) {

    val timeAtThisFrame = Date().getTime()
    val dt = (timeAtThisFrame - timeAtLastFrame).toFloat() / 1000.0f
    val t = (timeAtThisFrame - timeAtFirstFrame).toFloat() / 1000.0f
    timeAtLastFrame = timeAtThisFrame

    updateHeliCamera(gameObjects[0], camera, dt)
    camera.move(dt, keysPressed)
    camera.update()

    defaultFramebuffer.bind(gl)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    if (!keysPressed.isEmpty()) {
      frameCount = 0
    }
    frameCount = frameCount + 1
    camera.move(dt, keysPressed)

    val maxSteerAngle = PI.toFloat() / 6.0f
    val straightenSpeed = 2.0f
if(isControllable) {
  if ("LEFT" in keysPressed && "UP" in keysPressed) {
    gameObjects[0].yaw += dt * 2.0f
    val forward = Vec3(sin(gameObjects[0].yaw), 0.0f, cos(gameObjects[0].yaw))

    gameObjects[0].position = forward * dt * 34.0f + gameObjects[0].position
    for (i in 1 until 5) {
      if (i >= 3) {
        gameObjects[i].yaw = min(gameObjects[i].yaw + dt * straightenSpeed, maxSteerAngle)
      }
      gameObjects[i].pitch += dt * 8.0f
    }
  } else if ("RIGHT" in keysPressed && "UP" in keysPressed) {
    gameObjects[0].yaw -= dt * 2.0f
    val forward = Vec3(sin(gameObjects[0].yaw), 0.0f, cos(gameObjects[0].yaw))
    gameObjects[0].position = forward * dt * 34.0f + gameObjects[0].position
    for (i in 1 until 5) {
      if (i >= 3) {
        gameObjects[i].yaw = min(gameObjects[i].yaw + dt * straightenSpeed, -maxSteerAngle)
      }
      gameObjects[i].pitch += dt * 8.0f
    }
  } else if ("UP" in keysPressed) {
    val forward = Vec3(sin(gameObjects[0].yaw), 0.0f, cos(gameObjects[0].yaw))
    gameObjects[0].position = forward * dt * 50.0f + gameObjects[0].position
    for (i in 1 until 5) {
      gameObjects[i].pitch += dt * 10.0f
    }
  } else if ("DOWN" in keysPressed) {
    val forward = Vec3(sin(gameObjects[0].yaw), 0.0f, cos(gameObjects[0].yaw))
    gameObjects[0].position = gameObjects[0].position.minus(forward * dt * 14.0f)
    for (i in 1 until 5) {
      gameObjects[i].pitch -= dt * 5.0f
    }
  }
}

    if (!keysPressed.contains("LEFT") && !keysPressed.contains("RIGHT")) {
      for (i in 3 until 5) {
        if (gameObjects[i].yaw > 0) {
          gameObjects[i].yaw = max(gameObjects[i].yaw - dt * straightenSpeed, 0f)
        } else if (gameObjects[i].yaw < 0) {
          gameObjects[i].yaw = min(gameObjects[i].yaw + dt * straightenSpeed, 0f)
        }
      }
    }


    for (i in spheres.size - 1 downTo 0) {
      if (detectCollision(gameObjects[0], spheres[i])) {
        applyPushForce(gameObjects[0], spheres[i], 1f)
        spheres.removeAt(i)
      }
    }

    for (i in 0 until pushDirections.keys.size) {
      if (detectCollision(gameObjects[0], pushDirections.keys.elementAt(i))) {
        applyPushForce(gameObjects[0], pushDirections.keys.elementAt(i), 1f)
      }
    }

      for (i in 0 until spheres.size) {
        spheres[i].position.z -= dt * randomFloat(1f, 60f)
        spheres[i].pitch -= dt * 6.0f
      }


    val gravity = Vec3(0.0f, -9.81f, 0.0f)
    for (sphere in spheres) {
      if (sphere.position.x > 103f || sphere.position.x < -103f) {
        if (sphere.velocity.y == 0.0f) {
          sphere.velocity.y = -70.0f
        }
          sphere.velocity = sphere.velocity.plus(gravity * dt)
          sphere.position = sphere.position.plus(sphere.velocity * dt)
      }
    }
    for (i in 0 until pushDirections.size) {
      if (pushDirections.keys.elementAt(i).position.x > 103f || pushDirections.keys.elementAt(i).position.x < -103f) {
        if(pushDirections.keys.elementAt(i).velocity.y == 0.0f) {
          pushDirections.keys.elementAt(i).velocity.y = -70.0f
        }
        pushDirections.keys.elementAt(i).velocity = pushDirections.keys.elementAt(i).velocity.plus(gravity * dt)
        pushDirections.keys.elementAt(i).position = pushDirections.keys.elementAt(i).position.plus(pushDirections.keys.elementAt(i).velocity * dt)
      }
    }
    val car = gameObjects[0]
    if (car.position.x < -103f && calculateCarDirection(car.yaw) || car.position.x > 103f && !calculateCarDirection(car.yaw)) {
      if (car.velocity.y == 0.0f) {
        car.velocity.y = -70.0f
      }
      isControllable = false
      car.velocity = car.velocity.plus(gravity * dt)
      car.position = car.position.plus(car.velocity * dt)
      car.roll += dt * 5.0f
      car.pitch += dt * 3.0f
      car.yaw += dt * 2.0f
    }
    if (car.position.x > 103f && calculateCarDirection(car.yaw) || car.position.x < -103f && !calculateCarDirection(car.yaw)) {
        if (car.velocity.y == 0.0f) {
            car.velocity.y = -70.0f
        }
        isControllable = false
        car.velocity = car.velocity.plus(gravity * dt)
        car.position = car.position.plus(car.velocity * dt)
        car.roll -= dt * 5.0f
        car.pitch += dt * 3.0f
        car.yaw += dt * 2.0f
    }
      continueMovingSpheres(dt)

      gl.clearColor(0.3f, 0.0f, 0.3f, 1.0f)
      gl.clearDepth(1.0f)

      val spawn = ArrayList<GameObject>()
      val killList = ArrayList<GameObject>()
      gameObjects.forEach {
        if (!it.move(dt, t, keysPressed, gameObjects, spawn)) {
          killList.add(it)
        }
      }
      killList.forEach { gameObjects.remove(it) }
      spawn.forEach { gameObjects.add(it) }

      gameObjects.forEach { it.update() }
      spheres.forEach { it.update() }
      roads.forEach { it.update() }
      pushDirections.keys.forEach { it.update() }


      defaultFramebuffer.bind(gl)
      gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
      backgroundMesh.draw(camera)
      gameObjects.forEach { it.draw(camera, *lights) }
      spheres.forEach { it.draw(camera, *lights) }
      roads.forEach { it.draw(camera, *lights) }
      pushDirections.keys.forEach { it.draw(camera, *lights) }
    }
  }


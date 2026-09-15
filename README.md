# Networking Lab Part 2 — Mini Web Application

## 1. Descripción del proyecto

Este proyecto extiende un servidor HTTP mínimo basado en sockets (Java `ServerSocket`/`Socket`) en una pequeña aplicación web: sirve archivos estáticos (HTML, JavaScript, imágenes), expone cuatro servicios "hardcoded" que devuelven JSON, y corre desplegado en una única instancia de AWS EC2.

El propósito del laboratorio no es construir un servidor de producción, sino entender —desde cero, sin frameworks— cómo un navegador, una URL, una solicitud HTTP, una ruta del servidor, un tipo de respuesta y un host remoto encajan entre sí. Por eso el servidor es intencionalmente **secuencial** (atiende una conexión completa antes de aceptar la siguiente) y las rutas están **hardcodeadas** en vez de usar un framework de enrutamiento.

## 2. Metáfora de sistema y arquitectura

**Metáfora:** el servidor es como una **ventanilla de atención de un solo funcionario**. Un cliente (el navegador) llega, hace una solicitud, el funcionario la atiende por completo —lee el pedido, busca la respuesta, la entrega— y solo entonces atiende al siguiente cliente en la fila. No hay varios funcionarios trabajando en paralelo (no hay hilos); si el funcionario se demora con un cliente, todos los demás simplemente esperan en la fila. Eso es exactamente lo que este laboratorio hace visible antes de introducir concurrencia en una etapa futura.

**Componentes y responsabilidades:**

- **Navegador (cliente):** hace solicitudes HTTP —algunas automáticas (al cargar `index.html`, pide también `app.js` y las imágenes referenciadas), y otras disparadas por el usuario vía JavaScript asíncrono (los tres servicios interactivos).
- **JavaScript (`app.js`):** vive dentro del navegador. Intercepta los eventos de los formularios/botones, evita el envío tradicional (`preventDefault`), construye la URL del servicio con los datos del usuario, y usa `fetch` para pedirle datos al servidor sin recargar la página.
- **Servidor Java (`HttpServer`):** el "funcionario único". Escucha en un puerto TCP, acepta una conexión, lee la línea de solicitud (método + ruta + protocolo), decide si la ruta corresponde a un servicio hardcodeado o a un recurso estático, arma una respuesta HTTP válida (bytes, encabezados, `Content-Type`, `Content-Length`), la escribe, y cierra la conexión.
- **Recursos estáticos (`src/main/resources/public/`):** el HTML, JS e imágenes servidos tal cual, leídos como bytes crudos vía classpath.
- **Servicios hardcodeados:** cuatro rutas especiales reconocidas con comparaciones explícitas de string (no un router genérico): `/api/greeting`, `/api/square`, `/api/time`, `/api/health`.
- **Internet / Security Group / EC2:** la misma aplicación, sin cambios de arquitectura, corriendo detrás de un firewall (security group) en una instancia remota.

![Diagrama de arquitectura](img/arquitectura.png)

*(Diagrama: navegador → Internet → Security Group (EC2) → HttpServer.java (secuencial) → Static resources / Hardcoded services.)*

## 3. Decisiones de diseño

- **¿Por qué el servidor permanece secuencial?** El objetivo del laboratorio es observar el límite de un servidor de una sola conexión a la vez antes de resolverlo con concurrencia. Agregar hilos ahora ocultaría el comportamiento que se quiere hacer visible (ver la prueba de la sección 6.2 más abajo).
- **¿Por qué las rutas están hardcodeadas?** Un router genérico, basado en anotaciones o reflexión, resolvería el enrutamiento de forma automática pero ocultaría el mecanismo: cómo un `path` de texto plano se traduce en una decisión de negocio. Aquí cada ruta especial se reconoce con una comparación explícita de `String`.
- **¿Cómo se seleccionan los content-types?** Se extrae la extensión del archivo solicitado (`lastIndexOf(".")` + `substring`) y se busca en un `Map<String, String>` fijo (html, css, js, png, jpg/jpeg). Si la extensión no está mapeada, se responde `application/octet-stream` por defecto.
- **¿Cómo se rechazan rutas inseguras?** Se comprueba si el `path` solicitado contiene la secuencia `".."`; si es así, se responde `400 Bad Request` sin tocar el sistema de archivos. Además, los recursos se leen vía `getResourceAsStream` sobre el classpath (dentro del jar empaquetado), no directamente del disco con rutas de archivo arbitrarias, lo que reduce la superficie de ataque de traversal.
- **¿Por qué el cliente browser es asíncrono?** Para que la interfaz permanezca utilizable mientras espera la respuesta del servidor (aunque el servidor mismo siga siendo secuencial). Se usa `fetch` con `async/await`, `e.preventDefault()` para evitar el recargo de página, y manejo separado de errores HTTP (`res.ok`) y errores de red (`catch`).
- **Manejo de errores por conexión:** cada conexión se atiende dentro de su propio `try/catch`, y los recursos (`Socket`, `BufferedReader`, `OutputStream`) se declaran con *try-with-resources* para garantizar su cierre incluso si ocurre una excepción a mitad de la solicitud. Así, un error en un cliente nunca tumba el proceso completo del servidor.

## 4. Estructura del proyecto

```
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/lab/networking/server/
│   │   │   └── HttpServer.java        # servidor completo: ciclo de vida, recursos estáticos, servicios
│   │   └── resources/public/
│   │       ├── index.html
│   │       ├── app.js
│   │       └── images/
│   └── test/java/com/lab/networking/server/
│       └── (pruebas automatizadas)
├── img/                                # evidencia y diagrama
└── README.md
```

## 5. Prerrequisitos

- Java 21 (JDK)
- Apache Maven 3.9+
- Un navegador moderno
- (Para despliegue) cuenta de AWS con acceso a EC2, y una llave `.pem` para SSH

## 6. Instalación y construcción

```bash
git clone <URL-del-repositorio>
cd <nombre-del-repositorio>
mvn clean package
```

Esto genera un jar ejecutable en `target/*.jar` (contiene las clases y los recursos públicos empaquetados).

## 7. Cómo correr localmente

```bash
java -jar target/TDSE_LAB_NO.05-1.0.0.jar 8080
```

- El puerto es configurable: el primer argumento (`8080` en el ejemplo) define en qué puerto escucha. Si no se pasa ningún argumento, usa `8080` por defecto.
- Abrir `http://localhost:8080/` en el navegador.
- Para detener: `Ctrl+C` en la terminal donde corre el proceso.

## 8. Cómo usar la aplicación

La página principal ofrece tres acciones:

| Acción | Ruta de servicio | Parámetro | Respuesta exitosa | Error controlado |
|---|---|---|---|---|
| Saludo | `GET /api/greeting?name=<texto>` | `name` (string) | `{"greeting":"Hello, <name>!"}` | `400` si falta `name` |
| Cuadrado | `GET /api/square?value=<número>` | `value` (numérico) | `{"input":N,"square":N²}` | `400` si `value` no es numérico o falta |
| Hora del servidor | `GET /api/time` | — | `{"serverTime":"..."}` | — |
| Salud | `GET /api/health` | — | `{"status":"UP"}` | — |

Todas las llamadas se hacen de forma asíncrona desde `app.js`; los resultados aparecen en el área correspondiente sin recargar la página. Los errores de validación (400), de red, o cualquier respuesta HTTP no exitosa se muestran en el área de error visible en la página.

También se aceptan solicitudes `GET` sobre archivos estáticos (`/`, `/index.html`, `/app.js`, `/images/...`). Cualquier otro método (`POST`, etc.) responde `405 Method Not Allowed`; una ruta inexistente responde `404 Not Found`; una ruta con intento de salir del área pública (`..`) responde `400 Bad Request`.

## 9. Cómo correr las pruebas

El proyecto incluye pruebas automatizadas con JUnit 5 en `src/test/java/com/lab/networking/server/HttpServerTest.java`. También puedes validar el comportamiento manualmente con estos comandos:

```bash
mvn test
curl http://localhost:8080/
curl http://localhost:8080/index.html
curl "http://localhost:8080/api/greeting?name=Jacobo"
curl "http://localhost:8080/api/square?value=7"
curl http://localhost:8080/api/time
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/index.html                        # 405
curl --path-as-is "http://localhost:8080/../README.md"               # 400
curl http://localhost:8080/no-existe.html                            # 404
```

La suite de pruebas cubre la carga de la página principal, las rutas estáticas, los servicios JSON, los errores y la validación de traversal.

## 10. Despliegue en AWS EC2

1. Se construyó el artefacto localmente con `mvn clean package` y se probó (`java -jar ...`) antes de subirlo.
2. Se lanzó una instancia EC2 (Amazon Linux 2023, tipo `t2.micro`/`t3.micro`) con un security group que permitía SSH (puerto 22) solo desde la IP del administrador, y tráfico TCP en el puerto de la aplicación (8080) desde el origen aprobado por el curso.
3. Se instaló Java 21 en la instancia: `sudo dnf install -y java-21-amazon-corretto-headless`.
4. El jar se transfirió con `scp`.
5. La aplicación se configuró como servicio `systemd` (`/etc/systemd/system/networking-lab.service`), con `Restart=on-failure` y logs redirigidos a un archivo (`~/networking-lab.log`), de forma que siguiera corriendo después de cerrar la sesión SSH y se reiniciara automáticamente si fallaba.
6. Se verificó primero desde dentro de la instancia (`curl http://localhost:8080/api/health`) y luego desde el exterior usando la IP pública de la instancia.
7. **No se publica aquí la IP pública, ni la llave privada, ni ninguna credencial de AWS.**

Comandos clave usados en la instancia:
```bash
sudo systemctl daemon-reload
sudo systemctl enable networking-lab.service
sudo systemctl start networking-lab.service
sudo systemctl status networking-lab.service
```

**Estado actual:** la instancia EC2 usada para este laboratorio fue **terminada** una vez recolectada la evidencia (ver sección 12, limpieza), por lo que la aplicación ya no está accesible en línea. Toda la evidencia de que funcionó correctamente en AWS se encuentra en la sección 11.

## 11. Evidencia y resultados

| Evidencia | Archivo |
|---|---|
| Diagrama de arquitectura | `img/arquitectura.png` |
| Página cargando desde la IP pública de EC2 | `img/ipPublica.png` |
| Error controlado — path traversal (400 Bad Request) | `img/bad-request.png` |
| Error controlado — recurso inexistente (404 Not Found) | `img/404.png` |
| Error controlado — método no soportado (405 Method Not Allowed) | `img/405.png` |
| Pestaña Network del navegador (`/`, `/app.js`, `/images/...` en 200) | `img/status.png` |
| Servicio corriendo como systemd (`active (running)`) | `img/running.png`|
| Evidencia del límite secuencial (sección 6.2) | `img/lento.png` |

**Prueba del límite secuencial (sección 6.2):** se lanzó una solicitud lenta (`/api/time` con un retraso artificial de 8s) en segundo plano y, 0.5s después, una solicitud normalmente instantánea (`/api/health`). El resultado midió **7.5 segundos** para la segunda solicitud, confirmando que tuvo que esperar en la fila hasta que el servidor terminara de atender por completo la primera — ver `img/lento.png`.

## 12. Limpieza de recursos en AWS

- [x] Servicio detenido (`sudo systemctl stop networking-lab.service`) antes de terminar la instancia.
- [x] Instancia EC2 terminada (`Instance state → Terminate`), confirmado el estado `terminated` en la consola.
- [ ] Elastic IP liberada *(no se asignó ninguna en este laboratorio)*.
- [x] Security group del laboratorio eliminado.
- [x] Vista de costos revisada para confirmar que no queden recursos activos generando cargo.

## 13. Reflexión (preguntas de la sección 8.2)

**¿Por qué una sola página HTML causa varias solicitudes HTTP?**
Porque el HTML es solo el documento inicial: el navegador lo interpreta y descubre referencias a otros recursos (`<script src="...">`, `<img src="...">`), y cada una de esas referencias dispara una solicitud HTTP independiente. Una sola visión de página termina siendo, del lado del servidor, varias conexiones separadas —`/`, `/app.js`, y una por cada imagen.

**¿Por qué las respuestas de imagen deben tratarse como bytes en vez de texto?**
Porque una imagen no es texto: es un formato binario con codificación propia (PNG, JPEG). Si se leyera o escribiera como si fuera texto (por ejemplo con un `PrintWriter` que asume una codificación de caracteres), los bytes se corromperían al intentar reinterpretarlos como caracteres. Leer con `InputStream`/`readAllBytes()` y escribir con `OutputStream.write(byte[])` preserva los bytes exactos, sin importar el tipo de contenido.

**¿Cuál es el rol del content-type de la respuesta?**
Le dice al navegador cómo interpretar los bytes que recibió: como HTML a construir, como JavaScript a ejecutar, como una imagen a decodificar y pintar, o como JSON a parsear. Sin el `Content-Type` correcto, el navegador podría mostrar el contenido como texto plano o simplemente fallar al interpretarlo.

**¿Qué está hardcodeado en este diseño, y qué generalizaría eventualmente un framework de enrutamiento?**
Está hardcodeado el reconocimiento de cada ruta especial (`/api/greeting`, `/api/square`, `/api/time`, `/api/health`) mediante comparaciones explícitas de `String` dentro de `handleConnection`. Un framework de enrutamiento (como Spring MVC) generalizaría esto con anotaciones (`@GetMapping`) y un despachador central que resuelve automáticamente qué método invocar según la ruta, sin necesidad de escribir esas comparaciones a mano.

**¿Por qué el navegador puede seguir respondiendo mientras el servidor sigue atendiendo solicitudes de forma secuencial?**
Porque son dos capas independientes. El JavaScript asíncrono (`fetch` con `async/await`) evita que el hilo principal del navegador se bloquee esperando una respuesta —la pestaña sigue siendo interactiva. Pero eso no cambia nada del lado del servidor: el servidor sigue procesando una conexión completa antes de aceptar la siguiente. Lo asíncrono resuelve la experiencia del cliente, no la capacidad del servidor.

**¿Qué cambió al mover el servidor a EC2? ¿Qué no cambió?**
Cambió el host (de `localhost` a una máquina remota con IP pública) y la superficie de red expuesta (hubo que abrir puertos específicos en un security group, en vez de confiar en la red local). No cambió absolutamente nada del código de la aplicación ni de su arquitectura: el mismo jar, sin recompilar ni modificar, corrió igual en ambos entornos. Eso confirma que el servidor era independiente de su entorno de ejecución desde el diseño.

**¿Qué pasa cuando dos usuarios envían solicitudes lentas casi al mismo tiempo?**
El segundo usuario espera en la fila hasta que el servidor termine de atender por completo al primero. Se comprobó experimentalmente (sección 6.2): una solicitud normalmente instantánea (`/api/health`) tardó 7.5 segundos en responder porque llegó justo después de una solicitud artificialmente lenta (8 segundos) que el servidor todavía estaba atendiendo. El tiempo de espera del segundo usuario depende directamente de cuánto tarde el primero, sin importar los recursos de hardware disponibles —porque solo hay una conexión "en proceso" a la vez.

**¿Cuál sería la siguiente limitación arquitectónica a resolver, y por qué la concurrencia debería llegar antes que el balanceo de carga?**
La siguiente limitación es justamente la ausencia de concurrencia: un servidor de un solo hilo desperdicia capacidad de la máquina (CPU, memoria) mientras espera E/S de un cliente lento, y bloquea innecesariamente a todos los demás. Agregar concurrencia (hilos, un pool de conexiones) resuelve eso *dentro* de una sola instancia. El balanceo de carga, en cambio, distribuye tráfico *entre múltiples* instancias —no tiene sentido replicar instancias si cada una sigue siendo ineficiente por dentro; primero hay que aprovechar bien los recursos de una sola máquina antes de multiplicarla.

## 14. Limitaciones conocidas

- El servidor procesa **una conexión a la vez**; no hay concurrencia, hilos, ni pool de conexiones. Un cliente lento bloquea a todos los demás mientras se le atiende (demostrado en la sección 13).
- Solo soporta el método `GET`. Cualquier otro método responde `405`.
- Las rutas de servicio son un conjunto fijo y pequeño, reconocidas con comparaciones explícitas, no un framework de enrutamiento general.
- No hay autenticación, cifrado (HTTP, no HTTPS), ni persistencia de datos entre solicitudes.
- No es un servidor apto para producción: es un ejercicio educativo para entender los fundamentos de HTTP y sockets antes de introducir concurrencia y distribución.

## 15. Autor y agradecimientos

**Autor:** Jacobo

Basado en la guía de laboratorio del curso TDSE y en los tutoriales de networking de Java de Oracle (`docs.oracle.com/javase/tutorial/networking`). Este proyecto fue desarrollado como ejercicio académico para comprender HTTP, rutas, recursos estáticos y servicios hardcoded antes de introducir concurrencia y despliegue distribuido.
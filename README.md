# VoidEvent - Evento del Vacío

Plugin de Minecraft (Paper 1.21.1) para un evento temático del vacío en el End.

## Requisitos
- Paper 1.21.1+
- Java 21+
- Maven 3.9+

## Compilar
```bash
mvn clean package
```
El JAR se genera en `target/VoidEvent-1.0.0.jar`.

## Uso
1. Colocar el JAR en la carpeta `plugins/` del servidor
2. Reiniciar el servidor
3. Ir al End con los jugadores
4. Ejecutar `/voidevent start` para iniciar el evento

## Comando
- `/voidevent start` — Inicia la secuencia completa del evento
- `/voidevent stop` — Detiene el evento en curso
- `/voidevent reset` — Resetea el End a su estado original

## Secuencia del Evento
1. **Cinemática intro** — Muestra herramientas y armadura de netherita en la fuente del End
2. **Ritual** — Los objetos flotan y orbitan un huevo de dragón con efectos visuales
3. **Cristales** — Las torres disparan rayos a las herramientas, que reflejan al huevo
4. **El Gigante** — Aparece el villano principal, destruye cristales, invoca dragones
5. **Destrucción** — La isla se rompe, solo queda la fuente
6. **Pelea** — Dos dragones (azul y morado) con IA personalizada + islas flotantes
7. **El Gigante** — Fase final de boss con ataques personalizados
8. **Cinemática final** — Renace el Dragón de Luz, regenera la isla

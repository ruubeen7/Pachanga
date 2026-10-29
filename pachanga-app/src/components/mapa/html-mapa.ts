/**
 * La página web que se carga dentro del WebView para dibujar el mapa.
 *
 * ¿Por qué una página web? La versión "nativa" de MapLibre no viene incluida en Expo Go,
 * y compilar nuestra propia app para iPhone exige una cuenta de Apple de pago.
 * MapLibre GL JS (la versión web de la misma librería) funciona dentro de un WebView
 * sin compilar nada y cuesta 0 €.
 *
 * - Mapa base: OpenFreeMap (gratis, sin clave ni registro), con datos de OpenStreetMap.
 * - La app habla con la página con injectJavaScript → llama a window.pachanga.xxx(...)
 * - La página habla con la app con window.ReactNativeWebView.postMessage(...) → llega a onMessage.
 */

const VERSION_MAPLIBRE = '4.7.1';
const ESTILO_MAPA = 'https://tiles.openfreemap.org/styles/liberty';

export function crearHtmlMapa(latitud: number, longitud: number, zoom: number): string {
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/maplibre-gl@${VERSION_MAPLIBRE}/dist/maplibre-gl.css">
<script src="https://unpkg.com/maplibre-gl@${VERSION_MAPLIBRE}/dist/maplibre-gl.js"></script>
<style>
  html, body, #mapa { margin: 0; padding: 0; width: 100%; height: 100%; }
  body { background: #e8e4dc; }
  /* Chincheta de cancha. El tamaño se cambia en .dentro porque MapLibre usa "transform" del contenedor para colocarla. */
  .chincheta { cursor: pointer; }
  /* Cancha sin eventos: pequeña y algo apagada, para que destaquen las que tienen. */
  .chincheta .dentro {
    position: relative;
    width: 26px; height: 26px; border-radius: 50%;
    background: #E8A57A; border: 2px solid #fff; box-shadow: 0 1px 4px rgba(0,0,0,.3);
    display: flex; align-items: center; justify-content: center; font-size: 14px;
    transition: transform .15s;
  }
  .chincheta.elegida .dentro { transform: scale(1.3); background: #B9531A; }
  /* Cancha con eventos próximos: más grande, color fuerte, con una onda que late y el número de eventos. */
  .chincheta.con-eventos .dentro {
    width: 38px; height: 38px; font-size: 21px;
    background: #E8772E; border-width: 3px; box-shadow: 0 2px 8px rgba(0,0,0,.4);
  }
  .chincheta.con-eventos .dentro::before {
    content: ''; position: absolute; inset: -3px; border-radius: 50%;
    border: 3px solid #E8772E; animation: latido 1.8s ease-out infinite;
  }
  @keyframes latido {
    from { transform: scale(1); opacity: .8; }
    to { transform: scale(1.9); opacity: 0; }
  }
  .chincheta .numero {
    display: none; position: absolute; top: -6px; right: -8px;
    min-width: 18px; height: 18px; padding: 0 4px; box-sizing: border-box; border-radius: 9px;
    background: #D93025; color: #fff; border: 2px solid #fff;
    font: bold 11px/14px -apple-system, Roboto, sans-serif; text-align: center;
  }
  .chincheta.con-eventos .numero { display: block; }
  /* Punto azul de "estás aquí" */
  .yo { width: 14px; height: 14px; border-radius: 50%; background: #1A73E8;
        border: 3px solid #fff; box-shadow: 0 0 0 6px rgba(26,115,232,.25); }
</style>
</head>
<body>
<div id="mapa"></div>
<script>
  function enviar(mensaje) {
    if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(mensaje));
  }

  if (!window.maplibregl) {
    // El script no se pudo descargar (sin internet)
    enviar({ tipo: 'error', mensaje: 'No se pudo cargar el mapa. Revisa tu conexión.' });
  } else {
    var mapa = new maplibregl.Map({
      container: 'mapa',
      style: '${ESTILO_MAPA}',
      center: [${longitud}, ${latitud}], // MapLibre también usa (longitud, latitud)
      zoom: ${zoom},
      attributionControl: { compact: true },
    });
    var marcadores = {}; // id de cancha → chincheta
    var elegida = null;
    var yo = null;

    // Cada vez que el mapa se para (tras arrastrar o hacer zoom) avisamos a la app
    // del centro y del radio visible, para que pida las canchas de esa zona.
    function avisarMovido() {
      var centro = mapa.getCenter();
      var radio = centro.distanceTo(mapa.getBounds().getNorthEast()); // metros del centro a una esquina
      enviar({ tipo: 'movido', lat: centro.lat, lng: centro.lng, radio: Math.round(radio) });
    }

    // Resalta la chincheta si la cancha tiene eventos próximos y pone cuántos (9+ si son muchos).
    // Se llama cada vez que llegan canchas, porque el número puede cambiar.
    function pintarEventos(el, cuantos) {
      el.classList.toggle('con-eventos', cuantos > 0);
      el.querySelector('.numero').textContent = cuantos > 9 ? '9+' : String(cuantos);
      el.style.zIndex = cuantos > 0 ? '2' : '1'; // las de eventos, por encima de las demás
    }

    function marcarElegida(id) {
      elegida = id;
      Object.keys(marcadores).forEach(function (clave) {
        marcadores[clave].getElement().classList.toggle('elegida', String(clave) === String(id));
      });
    }

    // Si el WebView cambia de tamaño (al colocarse en pantalla, al girar el móvil...),
    // el mapa se redibuja ocupando todo; si no, se quedaría pintado solo en una esquina.
    new ResizeObserver(function () { mapa.resize(); }).observe(document.getElementById('mapa'));

    mapa.on('load', function () {
      // El estilo trae los nombres en inglés ("Seville"): usamos el español y, si no hay, el local.
      mapa.getStyle().layers.forEach(function (capa) {
        if (capa.type === 'symbol' && capa.layout && capa.layout['text-field']) {
          mapa.setLayoutProperty(capa.id, 'text-field', ['coalesce', ['get', 'name:es'], ['get', 'name']]);
        }
      });
      enviar({ tipo: 'listo' });
      avisarMovido();
    });
    mapa.on('moveend', avisarMovido);
    // Tocar el mapa fuera de una chincheta quita la selección
    mapa.on('click', function () {
      marcarElegida(null);
      enviar({ tipo: 'cancha', id: null });
    });

    window.pachanga = {
      // Pinta estas canchas y quita las que ya no están en la lista
      canchas: function (lista) {
        var nuevos = {};
        lista.forEach(function (cancha) {
          var marcador = marcadores[cancha.id];
          if (!marcador) {
            var el = document.createElement('div');
            el.className = 'chincheta';
            var dentro = document.createElement('div');
            dentro.className = 'dentro';
            dentro.textContent = '🏀';
            var numero = document.createElement('div');
            numero.className = 'numero';
            dentro.appendChild(numero);
            el.appendChild(dentro);
            el.addEventListener('click', function (evento) {
              evento.stopPropagation();
              marcarElegida(cancha.id);
              enviar({ tipo: 'cancha', id: cancha.id });
            });
            marcador = new maplibregl.Marker({ element: el }).setLngLat([cancha.longitud, cancha.latitud]).addTo(mapa);
          }
          pintarEventos(marcador.getElement(), cancha.eventosProximos || 0);
          nuevos[cancha.id] = marcador;
          delete marcadores[cancha.id];
        });
        Object.keys(marcadores).forEach(function (clave) { marcadores[clave].remove(); });
        marcadores = nuevos;
        marcarElegida(elegida);
      },
      centrar: function (lat, lng, zoom) {
        mapa.easeTo({ center: [lng, lat], zoom: zoom || mapa.getZoom() });
      },
      // Mueve el mapa para que se vean todos estos puntos
      encuadrar: function (puntos) {
        if (puntos.length === 1) {
          mapa.easeTo({ center: [puntos[0].longitud, puntos[0].latitud], zoom: 15 });
          return;
        }
        var limites = new maplibregl.LngLatBounds();
        puntos.forEach(function (p) { limites.extend([p.longitud, p.latitud]); });
        mapa.fitBounds(limites, { padding: { top: 140, bottom: 180, left: 50, right: 50 }, maxZoom: 15 });
      },
      yo: function (lat, lng) {
        if (!yo) {
          var el = document.createElement('div');
          el.className = 'yo';
          yo = new maplibregl.Marker({ element: el }).setLngLat([lng, lat]).addTo(mapa);
        } else {
          yo.setLngLat([lng, lat]);
        }
      },
      elegir: function (id) { marcarElegida(id); },
    };
  }
</script>
</body>
</html>`;
}

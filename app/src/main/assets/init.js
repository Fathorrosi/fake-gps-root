var urlParams = new URLSearchParams(window.location.search);
var lat = urlParams.get('lat') ?? 12;
var lng = urlParams.get('lng') ?? 15;
var zoom = urlParams.get('zoom') ?? 12;
var provider = urlParams.get('provider') ?? 'OpenStreetMap';

var map = L.map('map').setView([lat, lng], zoom);
var popup = L.popup();
var alreadyRunning = false;

var searchInput = document.getElementById("address_input");
searchInput.addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        event.preventDefault();
        searchForAddress();
    }
});

L.tileLayer.provider(provider, { className: 'map-tiles' }).addTo(map);

var icon = L.icon({
    iconUrl: 'marker-icon-2x.png',
    shadowUrl: 'marker-shadow.png',
    iconSize: [27, 44],
    shadowSize: [50, 64],
    iconAnchor: [14, 48],
    shadowAnchor: [17, 68],
    popupAnchor: [0, 0]
});

function safeCallAndroid(method, ...args) {
    if (typeof Android !== "undefined" && typeof Android[method] === "function") {
        Android[method](...args);
    } else {
        console.warn("Android bridge not available yet: " + method);
    }
}

function searchForAddress() {
    const query = searchInput.value;
    if (query && typeof Android !== "undefined" && Android.searchAddress) {
        Android.searchAddress(query);
    } else {
        console.warn("Android bridge not available for search");
    }
}

function onMapClick(e) {
    if (typeof mapMarker !== 'undefined') map.removeLayer(mapMarker);
    mapMarker = L.marker(e.latlng, { icon: icon }).addTo(map);
    var wrap = e.latlng.wrap().toString();
    safeCallAndroid("setPosition", wrap);
}

function onZoomEnd(e) {
    safeCallAndroid("setZoom", map.getZoom());
}

function setOnMap(aLat, aLng) {
    if (typeof mapMarker !== 'undefined') map.removeLayer(mapMarker);
    zoom = map.getZoom();
    map.setView(new L.LatLng(aLat, aLng), zoom);
    mapMarker = L.marker([aLat, aLng], { icon: icon }).addTo(map);
    alreadyRunning = true;
}

map.on('contextmenu', onMapClick);
map.on('zoomend', onZoomEnd);

// initial marker
var mapMarker = L.marker([lat, lng], { icon: icon }).addTo(map);

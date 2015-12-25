  function authority() {
        var url = window.location.href;
        if (url.startsWith("file")) {
            return "http://localhost:9000"
        } 
        return ""
    }

function getColor(time, cpu) {
    var treshold = new Date().getTime() - (60 * 1000);
    if ( time < treshold ) {
        return "#c0c0c0"
    }
    return "#9999ff"    
}

function updateData(nodesCallback, edgesCallback) {
    
    $.getJSON(authority() + "/nodes", function(nodes) {
        nodesCallback(nodes.map(function(item){
            return {
                id: item._id.addr,
                label: item._id.addr,
                color: getColor(item.last, item.cpu)
                
            }
        }));
        
        $.getJSON(authority() + "/edges", function(edges) {
            var max = edges.reduce(function(acc, item) { return item.last > acc ? item.last : acc }, 0);
            edgesCallback( edges.map( function(item) {
                var idarr = [item._id.source, item._id.dest ].sort();
                return {
                    id: idarr[0] + "_" + idarr[1], 
                    from: item._id.source,
                    to: item._id.dest,
                    length: 300 + Math.floor((item.last / max) * ( 2000 - 300 ) ),
                    //hidden: true,
                    color: {
                        color: "rgba(100, 100, 100, 0.1)",
                        hover: "rgba(100, 100, 255, 0.5)",
                        highlight: "#6699ff"
                    }
                }
            } ) );
        } );
        
    }).fail(function( jqxhr, textStatus, error ) {
        var err = textStatus + ", " + error;
        console.log( "Request Failed: " + err );
    });
    
};

function updateTelemetry(cpuChart, memChart, addr) {
    if (!addr) return;
    
    var query = encodeURI(JSON.stringify({ addr: addr }));
    var sort = encodeURI(JSON.stringify({ timestamp: -1 }));
    $.getJSON(authority() + "/db/telemetry?q=" + query + "&sort=" + sort + "&limit=1", function(data) {
        if (!!data && data.length > 0) {
            renderTelemetry(cpuChart, memChart, data);
        }
    });
}

function renderTelemetry(cpuChart, memChart, telemetry) {
    var rows = telemetry.map(function(item) {
        return { cpu: Math.floor(item.cpu * 100), mem: Math.floor(item.memory) }  
    });
    var cpuMedian = rows.reduce(function(a,b) { return a + b.cpu; }, 0 ) / rows.length;
    cpuChart.segments[0].value = cpuMedian;
    cpuChart.segments[1].value = 100 - cpuMedian;
    cpuChart.update();
    $("#cpu_value").text("CPU " + cpuMedian + "%");
    
    var memMedian = rows.reduce(function(a,b) { return a + b.mem; }, 0 ) / rows.length;
    memChart.segments[0].value = memMedian;
    memChart.segments[1].value = 100 - memMedian;
    memChart.update();
    $("#mem_value").text("RAM " + memMedian + "%");
}

var glSelectedNode = undefined

function addItem(data, dataSet) {
    data.forEach(function(item) { 
        if (!dataSet.get(item.id))
            dataSet.add(item);
    });
}

function LatencyVisualizer(from, to) {
    $('.telemetry-history').show("fast");
    $('#telemetry-title').html(from + " &rarr; " + to);
    var latencyContainer = $('#latency_viz')[0];
    var latencyDataset = new vis.DataSet();
    var options = {
        height: "25vh", 
        width: "100%", 
        interpolation: false,
        drawPoints: false,
        shaded: true,
        dataAxis : {
            showMinorLabels: false
        }
    };
    var graph2d = new vis.Graph2d(latencyContainer, latencyDataset, options);
    var query = encodeURI(JSON.stringify({ source: from, dest: to }));
    var sort = encodeURI(JSON.stringify({ time: -1 }));
    var isRunning = true;
    
    function plot() {
        if (!isRunning) {
            return;
        }
        $.getJSON(authority() + "/db/latency?q=" + query + "&sort=" + sort + "&limit=20", function(data) {
            var ltn = data.map(function(item) { 
               return {
                   x: item.time,
                   y: item.pingTotal
               }
            }).reverse();
            var min = ltn[0].x;
            var max = latencyDataset.max('x');
            max = max == null ? 0 : max.x;
            var old = latencyDataset.getIds({
                filter: function(it) {
                    return it.x < min;
                }
            });
            latencyDataset.remove(old);
            
            graph2d.setWindow(min, ltn[ltn.length - 1].x);
            
            latencyDataset.add(ltn.filter(function(item){ return item.x > max; }));
        });
        
        setTimeout( plot, 1000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
        $('.telemetry-history').hide();
    }
    
}

var latencyVisualizer;

$(function() {
    
    var nodes = new vis.DataSet(),
        edges = new vis.DataSet();
            
    var container = $('#graph')[0];
    var data = {
        nodes: nodes,
        edges: edges
    };
    var options = {
        nodes: {
            shape: "dot",
            font: {
                size: 10
            }
        },
        edges: {
            smooth: false,
        },
         physics: {
                barnesHut: {
                    "gravitationalConstant": -15100,
                    "springLength": 60,
                    "springConstant": 0.1,
                    "damping": 1
                },
                "maxVelocity": 150,
                "minVelocity": 0.75,
                stabilization: {
                    iterations: 20
                }
            }
    };
    var network = new vis.Network(container, data, options);
    
    function showLatencyTelemetry(from, to) {
          if (!!latencyVisualizer) {
            latencyVisualizer.stop();
            latencyVisualizer = undefined;
        }
        latencyVisualizer = new LatencyVisualizer(from, to);
    }
    
    network.on("click", function(e) {
        if ( !!e.edges && e.edges.length > 0 && !e.nodes.length ) {
            var edgeId = e.edges[0];
            var edge = edges.get(edgeId);
            showLatencyTelemetry(edge.from, edge.to);
            $('.from').val(edge.from);
            $('.to').val(edge.to);
        } else {
            $('.from').val("");
            $('.to').val("");
        }
    });
    
    var animationOptions = {
        offset: {x: 0, y: 0},
        duration: 1000,
        easingFunction: "easeInOutQuad"
      
    }
    
    function selectFromList(item) {
        network.selectNodes([item]);
        var from = $('.from').val();
        var to = $('.to').val();
        
        if (!!from && !!to) {
            var idarr = [from, to].sort();
            network.selectEdges([idarr[0] + "_" + idarr[1]]);
            showLatencyTelemetry(from, to)
        }
    }
    $('.telemetry-history').hide();
    var oldNodes = "";
    updateData(
        function(data) { 
            addItem(data, nodes); 
            if ( oldNodes !== data.toString() ) {
                $(".typehead").typeahead({source: data.map(function(item){ return item.id; }), afterSelect: selectFromList});
                oldNodes = data.toString();
            }
           // network.fit({animation: animationOptions}); 
        }, 
        function(data) { 
            addItem(data, edges); 
            network.fit({animation: animationOptions}); 
    });
    
    /*setInterval(function() { 
        updateData(function(data) { addItem(data, nodes); }, function(data) { addItem(data, edges); /*network.fit();*/ //});
   // }, 10000);*/
    
});
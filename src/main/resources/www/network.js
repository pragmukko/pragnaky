function updateData(nodesCallback, edgesCallback) {
    
    $.getJSON("http://localhost:9000/db/nodes?q=%7B%7D", function(nodes) {
        nodesCallback(nodes.map(function(item){
            return {
                id: item.addr,
                label: item.addr
            }
        }));
        
        $.getJSON("http://localhost:9000/edges", function(edges) {
            edgesCallback( edges.map( function(item) {
                return {
                    id: item._id.source + "_" + item._id.dest, 
                    from: item._id.source,
                    to: item._id.dest
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
    var sort = encodeURI(JSON.stringify({ $natural: -1 }));
    $.getJSON("http://localhost:9000/db/telemetry?q=" + query + "&sort=" + sort + "&limit=1", function(data) {
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
            dataSet.add(data);
    });
}

function LatencyVisualizer(from, to) {
    var latencyContainer = $('#latency_viz')[0];
    var latencyDataset = new vis.DataSet();
    var graph2d = new vis.Graph2d(latencyContainer, latencyDataset, {height: "300px"});
    
    var query = encodeURI(JSON.stringify({ source: from, dest: to }));
    var sort = encodeURI(JSON.stringify({ time: -1 }));
    var isRunning = true;
    
    function plot() {
        if (!isRunning) {
            return;
        }
        $.getJSON("http://localhost:9000/db/latency?q=" + query + "&sort=" + sort + "&limit=20", function(data) {
            var ltn = data.map(function(item) { 
               return {
                   x: new Date(item.time),
                   y: item.pingTotal
               }
            }).reverse();
            latencyDataset.clear();
            latencyDataset.add(ltn);
        });
        
        setTimeout( plot, 1000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
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
    var options = {};
    var network = new vis.Network(container, data, options);
        
    var cpu_ctx = $("#cpu_cnv")[0].getContext("2d");
    var mem_ctx = $("#mem_cnv")[0].getContext("2d");
    
    var data = [
        {
            value: 0,
            color: "#46BFBD",
            highlight: "#5AD3D1",
            label: "CPU %"
        },
        {
            value: 100,
            color: "#ffffff",
            color: "#ffffff"
        }
    ];
    var cpuChart = new Chart(cpu_ctx).Doughnut(data, { showTooltips : false, percentageInnerCutout: 70 });
    var memChart = new Chart(mem_ctx).Doughnut(data, { showTooltips : false, percentageInnerCutout: 70 });
    
    
    network.on("click", function(e) {
        if (!!latencyVisualizer) {
            latencyVisualizer.stop();
            latencyVisualizer = undefined;
        }
        if ( !!e.edges && e.edges.length > 0 ) {
            var edgeId = e.edges[0];
            var edge = edges.get(edgeId);
            latencyVisualizer = new LatencyVisualizer(edge.from, edge.to);
        }
        var nodeId = e.nodes[0];
        var node = nodes.get(nodeId);
        glSelectedNode = node.label;
        updateTelemetry(cpuChart, node.label);
    });
    
    setInterval(function() { updateTelemetry(cpuChart, memChart, glSelectedNode) }, 1000);
    
    setInterval(function() {updateData(function(data) { addItem(data, nodes); },function(data) { addItem(data, edges); });}, 1000 );
});
'use strict';

function authority() {
    var url = window.location.href;
    if (url.startsWith("file")) {
        return "http://193.105.219.176:9000";
    }
    return "";
}

function getColor(time) {
    var treshold = new Date().getTime() - (60 * 1000);
    if (time < treshold) {
        return "#c0c0c0";
    }
    return "#9999ff";
}

function getNetAddr(addr, tokensCount) {
    tokensCount = tokensCount || 3;
    var tokens = addr.split(".");
    return tokens.slice(0, tokensCount).join(".");
}

function diferentNetwork(from, to, tokensCount) {
    var fromTokens = from.split("."), toTokens = to.split("."), i;
    for (i = 0; i < tokensCount; i++) {
        if (fromTokens[i] !== toTokens[i]) return 1;
    }
    return 0;
}

function updateData(nodesCallback, edgesCallback) {
        
        $.getJSON(authority() + "/edges", function(edges) {
            var max = edges.reduce(function(acc, item) { return item.last > acc ? item.last : acc }, 0);
            var nodes = {};
            var edgArr =edges.map( function(item) {
                var idarr = [item._id.source, item._id.dest ].sort();
                nodes[item._id.source] = 0;
                nodes[item._id.dest] = 0;
                return {
                    id: idarr[0] + "_" + idarr[1], 
                    from: idarr[0],
                    to: idarr[1],
                    length: 1000 + Math.floor((item.last / max) * ( 1000 - 300 ) ) + diferentNetwork(item._id.source, item._id.dest, 3) * 500,
                    //hidden: true,
                    color: {
                        color: "rgba(100, 100, 100, 0.1)",
                        hover: "rgba(100, 100, 255, 0.5)",
                        highlight: "#6699ff"
                    }
                }
            } );
            var nodesArr = [];
            for (var n in nodes) nodesArr.push(n);
            nodesCallback(nodesArr.map(function(item){
                return {
                    id: item,
                    label: item,
                    group: getNetAddr(item)
                    
                }
            }));
            edgesCallback(edgArr);
        
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

function TelemetryVizualizer(host) {
    $('.telemetry-history').show("fast");
    $('#telemetry-title').html(host);
    var telemetryContainer = $('#latency_viz')[0];
    var telemetryDataset = new vis.DataSet();
    var groups = new vis.DataSet();
    groups.add({
        id: 1,
        content: "CPU",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    groups.add({
        id: 2,
        content: "Memory",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    var options = {
        height: "25vh", 
        width: "100%", 
        interpolation: false,
        drawPoints: false,
        legend: true,
        /*dataAxis : {
            showMinorLabels: false
        }*/
    };
    var graph2d = new vis.Graph2d(telemetryContainer, telemetryDataset, groups, options);
    var query = encodeURI(JSON.stringify({ addr: host }));
    var sort = encodeURI(JSON.stringify({ timestamp: -1 }));
    var isRunning = true;
    
    function plot() {
        if (!isRunning) {
            return;
        }
        
         $.getJSON(authority() + "/db/telemetry?q=" + query + "&sort=" + sort, function(data) {
            if (!!data && data.length > 0) {
                var ltn = data.reduce(function(acc, item) {
                    acc.push({y: Math.floor(item.cpu * 100), x: item.timestamp, group: 1 });
                    acc.push({y: Math.floor(item.memory), x: item.timestamp, group: 2 });
                    return acc;
                }, [] );
                telemetryDataset.update(ltn);
                graph2d.fit();
                
            }
        });
      
        setTimeout( plot, 5000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
        $('.telemetry-history').hide();
    }
    
}

var glSelectedNode = undefined

function addItem(data, dataSet) {
    data.forEach(function(item) { 
        if (!dataSet.get(item.id))
            dataSet.add(item);
    });
}

function LatencyVizualizer(from, to) {
    $('.telemetry-history').show("fast");
    $('#telemetry-title').html(from + " &rarr; " + to);
    var latencyContainer = $('#latency_viz')[0];
    var latencyDataset = new vis.DataSet();
    var groups = new vis.DataSet();
    groups.add({
        id: 1,
        content: "Direct",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    groups.add({
        id: 2,
        content: "Reverse",
        options: {
            interpolation: false,
            drawPoints: false,
            shaded: false,
        }});

    var options = {
        height: "25vh", 
        width: "100%", 
        interpolation: false,
        drawPoints: false,
        legend: true,
        dataAxis : {
            showMinorLabels: false
        }
    };
    var graph2d = new vis.Graph2d(latencyContainer, latencyDataset, groups, options);
    var query = encodeURI(JSON.stringify({ source: from, dest: to }));
    var reverseQuery = encodeURI(JSON.stringify({ source: to, dest: from }));
    var sort = encodeURI(JSON.stringify({ time: -1 }));
    var isRunning = true;
    
    function plot() {
        if (!isRunning) {
            return;
        }
        var plainReq = $.getJSON(authority() + "/db/latency?q=" + query + "&sort=" + sort);
        var reverseReq = $.getJSON(authority() + "/db/latency?q=" + reverseQuery + "&sort=" + sort);
        $.when(plainReq, reverseReq).done(function(dataPlain, dataReverse) {
            var ltn1 = dataPlain[0].map(function(item) { 
               return {
                   x: item.time,
                   y: item.pingTotal,
                   group: 1
               }
            }).reverse();
            var ltn2 = dataReverse[0].map(function(item) { 
               return {
                   x: item.time,
                   y: item.pingTotal,
                   group: 2
               }
            }).reverse();
            var arr = ltn1.concat(ltn2);
            
            latencyDataset.update(arr);
            graph2d.fit();
            
        });
    
        setTimeout( plot, 10000 );
        
    }
    
    plot();
    
    this.stop = function() {
        isRunning = false;
        graph2d.destroy();
        $('.telemetry-history').hide();
    }
    
}

var vizualizer;

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
            size: 10,
            font: {
                size: 10
            }
        },
        edges: {
            smooth: false,
        },
         physics: {
                barnesHut: {
                    "gravitationalConstant": -151000,
                    "springLength": 60,
                    "springConstant": 0.01,
                    "damping": 1
                },
                "maxVelocity": 150,
                "minVelocity": 0.75,
                stabilization: {
                    iterations: 20
                }
            }
    };
    
     var animationOptions = {
        offset: {x: 0, y: 0},
        duration: 1000,
        easingFunction: "easeInOutQuad"  
    }
    
    var network = new vis.Network(container, data, options);
    
    function showLatency(from, to) {
        if (!!vizualizer) {
            vizualizer.stop();
            vizualizer = undefined;
        }
        vizualizer = new LatencyVizualizer(from, to);
    }
    
    function showTelemetry(host) {
        if (!!vizualizer) {
            vizualizer.stop();
            vizualizer = undefined;
        }
        vizualizer = new TelemetryVizualizer(host);
    }
    
    network.on("click", function(e) {
        if ( !!e.edges && e.edges.length > 0 && !e.nodes.length ) {
            var edgeId = e.edges[0];
            var edge = edges.get(edgeId);
            showLatency(edge.from, edge.to);
            $('.from').val(edge.from);
            $('.to').val(edge.to);
        } else if ( !!e.nodes && e.nodes.length > 0 ) {
            var node = e.nodes[0];
            showTelemetry(node);
            $('.from').val(node);
            $('.to').val("");
        } else {
            $('.from').val("");
            $('.to').val("");
        }
    });
    
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
            if ( oldNodes !== data.toString() ) {
                addItem(data, nodes); 
                $(".typehead").typeahead({source: data.map(function(item){ return item.id; }), afterSelect: selectFromList});
                oldNodes = data.toString();
                data.forEach(function(item) {
                    $(".node-list").append("<div class='node-list-item'><span>" + item.id + "</span></div>");    
                });
                $('.node-list-item').on('click', function(element) {
                    var value = $(this).text();
                    network.selectNodes([value]);
                    $('.from').val(value);
                    $('.to').val("");
                    $('.node-list').hide();
                    network.focus(value, {animation: animationOptions});
                });
            }
           // network.fit({animation: animationOptions}); 
        }, 
        function(data) { 
            addItem(data, edges); 
            network.fit({animation: animationOptions}); 
    });
    
    $(".node-list").hide();
    
    $(".node-list-btn").on('click', function(){
        var nodeLst = $(".node-list");
       if ( nodeLst.is(":visible") ) 
           nodeLst.hide();
        else
            nodeLst.show();
    });
    
    /*setInterval(function() { 
        updateData(function(data) { addItem(data, nodes); }, function(data) { addItem(data, edges); /*network.fit();*/ //});
   // }, 10000);*/
    
});
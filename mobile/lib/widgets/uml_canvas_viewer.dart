import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../models/uml_element.dart';
import '../models/uml_relation.dart';

class UmlCanvasViewer extends StatefulWidget {
  final List<ElementDetailModel> elements;
  final List<RelationDetailModel> relations;
  final Function(ElementDetailModel element) onElementTap;
  final Function(ElementDetailModel element) onElementDelete;
  final Function(RelationDetailModel relation)? onRelationTap;
  final Function(RelationDetailModel relation)? onRelationDelete;
  final Function(ElementDetailModel element, double newX, double newY)? onElementMoved;

  const UmlCanvasViewer({
    super.key,
    required this.elements,
    required this.relations,
    required this.onElementTap,
    required this.onElementDelete,
    this.onRelationTap,
    this.onRelationDelete,
    this.onElementMoved,
  });

  @override
  State<UmlCanvasViewer> createState() => _UmlCanvasViewerState();
}

class _UmlCanvasViewerState extends State<UmlCanvasViewer> {
  final TransformationController _transformController = TransformationController();
  final double _canvasWidth = 3000.0;
  final double _canvasHeight = 3000.0;

  @override
  void dispose() {
    _transformController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Map of element positions for relation rendering
    final elementMap = {for (var e in widget.elements) e.id: e};

    return Container(
      color: const Color(0xFFF1F5F9), // slate-100 background
      child: InteractiveViewer(
        transformationController: _transformController,
        boundaryMargin: const EdgeInsets.all(500),
        minScale: 0.2,
        maxScale: 2.5,
        constrained: false,
        child: SizedBox(
          width: _canvasWidth,
          height: _canvasHeight,
          child: Stack(
            children: [
              // Grid background lines
              CustomPaint(
                size: Size(_canvasWidth, _canvasHeight),
                painter: _GridBackgroundPainter(),
              ),

              // UML Relations layer
              CustomPaint(
                size: Size(_canvasWidth, _canvasHeight),
                painter: _UmlRelationsPainter(
                  elements: elementMap,
                  relations: widget.relations,
                ),
              ),

              // UML Elements layer (cards)
              ...widget.elements.map((element) {
                final posX = element.posicionX.clamp(0.0, _canvasWidth - 260.0);
                final posY = element.posicionY.clamp(0.0, _canvasHeight - 200.0);

                return Positioned(
                  left: posX,
                  top: posY,
                  child: _UmlElementCard(
                    element: element,
                    onTap: () => widget.onElementTap(element),
                    onDelete: () => widget.onElementDelete(element),
                    onPanUpdate: widget.onElementMoved != null
                        ? (dx, dy) {
                            final nx = math.max(0.0, element.posicionX + dx);
                            final ny = math.max(0.0, element.posicionY + dy);
                            widget.onElementMoved!(element, nx, ny);
                          }
                        : null,
                  ),
                );
              }),
            ],
          ),
        ),
      ),
    );
  }
}

class _GridBackgroundPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0xFFCBD5E1).withValues(alpha: 0.4)
      ..strokeWidth = 1.0;

    const step = 40.0;
    for (double x = 0; x < size.width; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y < size.height; y += step) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _UmlRelationsPainter extends CustomPainter {
  final Map<String, ElementDetailModel> elements;
  final List<RelationDetailModel> relations;

  _UmlRelationsPainter({
    required this.elements,
    required this.relations,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final linePaint = Paint()
      ..color = const Color(0xFF334155)
      ..strokeWidth = 2.0
      ..style = PaintingStyle.stroke;

    final fillPaint = Paint()
      ..color = const Color(0xFF334155)
      ..style = PaintingStyle.fill;

    final whiteFillPaint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.fill;

    for (final rel in relations) {
      final source = elements[rel.elementoOrigen];
      final target = elements[rel.elementoDestino];
      if (source == null || target == null) continue;

      // Approximate node dimensions
      final sWidth = math.max(elementWidth(source), 220.0);
      final sHeight = math.max(elementEstimatedHeight(source), 120.0);
      final tWidth = math.max(elementWidth(target), 220.0);
      final tHeight = math.max(elementEstimatedHeight(target), 120.0);

      final sCenter = Offset(source.posicionX + sWidth / 2, source.posicionY + sHeight / 2);
      final tCenter = Offset(target.posicionX + tWidth / 2, target.posicionY + tHeight / 2);

      // Connect perimeter boundaries
      final start = _intersectRect(sCenter, tCenter, Rect.fromLTWH(source.posicionX, source.posicionY, sWidth, sHeight));
      final end = _intersectRect(tCenter, sCenter, Rect.fromLTWH(target.posicionX, target.posicionY, tWidth, tHeight));

      final angle = math.atan2(end.dy - start.dy, end.dx - start.dx);

      // Draw line (dashed for DEPENDENCY or REALIZACION)
      final isDashed = rel.tipoRelacion == 'DEPENDENCIA' || rel.tipoRelacion == 'REALIZACION';
      if (isDashed) {
        _drawDashedLine(canvas, start, end, linePaint);
      } else {
        canvas.drawLine(start, end, linePaint);
      }

      // Draw arrowhead / decoration at end or start
      _drawMarker(canvas, rel.tipoRelacion, end, angle, linePaint, fillPaint, whiteFillPaint);

      // Draw Multiplicity texts
      if (rel.multiplicidadOrigen != null && rel.multiplicidadOrigen!.isNotEmpty) {
        _drawText(
          canvas,
          rel.multiplicidadOrigen!,
          Offset(
            start.dx + 20 * math.cos(angle) - 15 * math.sin(angle),
            start.dy + 20 * math.sin(angle) + 15 * math.cos(angle),
          ),
          isBold: true,
        );
      }

      if (rel.multiplicidadDestino != null && rel.multiplicidadDestino!.isNotEmpty) {
        _drawText(
          canvas,
          rel.multiplicidadDestino!,
          Offset(
            end.dx - 35 * math.cos(angle) - 15 * math.sin(angle),
            end.dy - 35 * math.sin(angle) + 15 * math.cos(angle),
          ),
          isBold: true,
        );
      }

      // Relation name in center
      if (rel.nombreRelacion != null && rel.nombreRelacion!.isNotEmpty) {
        final mid = Offset((start.dx + end.dx) / 2, (start.dy + end.dy) / 2 - 14);
        _drawText(canvas, rel.nombreRelacion!, mid, isBold: false, isBadge: true);
      }
    }
  }

  void _drawDashedLine(Canvas canvas, Offset p1, Offset p2, Paint paint) {
    const dashWidth = 8.0;
    const dashSpace = 5.0;
    final distance = (p2 - p1).distance;
    final unit = (p2 - p1) / distance;
    double current = 0.0;

    while (current < distance) {
      final start = p1 + unit * current;
      final len = math.min(dashWidth, distance - current);
      canvas.drawLine(start, start + unit * len, paint);
      current += dashWidth + dashSpace;
    }
  }

  void _drawMarker(
    Canvas canvas,
    String type,
    Offset point,
    double angle,
    Paint stroke,
    Paint fill,
    Paint whiteFill,
  ) {
    canvas.save();
    canvas.translate(point.dx, point.dy);
    canvas.rotate(angle);

    switch (type) {
      case 'HERENCIA':
      case 'GENERALIZACION':
      case 'REALIZACION':
        // Closed triangle (hollow / white)
        final path = Path()
          ..moveTo(0, 0)
          ..lineTo(-16, -9)
          ..lineTo(-16, 9)
          ..close();
        canvas.drawPath(path, whiteFill);
        canvas.drawPath(path, stroke);
        break;

      case 'COMPOSICION':
        // Filled diamond
        final path = Path()
          ..moveTo(0, 0)
          ..lineTo(-12, -7)
          ..lineTo(-24, 0)
          ..lineTo(-12, 7)
          ..close();
        canvas.drawPath(path, fill);
        canvas.drawPath(path, stroke);
        break;

      case 'AGREGACION':
        // Hollow diamond
        final path = Path()
          ..moveTo(0, 0)
          ..lineTo(-12, -7)
          ..lineTo(-24, 0)
          ..lineTo(-12, 7)
          ..close();
        canvas.drawPath(path, whiteFill);
        canvas.drawPath(path, stroke);
        break;

      case 'DEPENDENCIA':
      case 'ASOCIACION':
      default:
        // Open arrow
        final path = Path()
          ..moveTo(-12, -7)
          ..lineTo(0, 0)
          ..lineTo(-12, 7);
        canvas.drawPath(path, stroke);
        break;
    }

    canvas.restore();
  }

  void _drawText(Canvas canvas, String text, Offset position, {bool isBold = false, bool isBadge = false}) {
    final textSpan = TextSpan(
      text: text,
      style: TextStyle(
        color: const Color(0xFF1E293B),
        fontSize: 12,
        fontWeight: isBold ? FontWeight.bold : FontWeight.w500,
        backgroundColor: isBadge ? Colors.white.withValues(alpha: 0.9) : Colors.transparent,
      ),
    );

    final textPainter = TextPainter(
      text: textSpan,
      textDirection: TextDirection.ltr,
    );
    textPainter.layout();

    if (isBadge) {
      final rect = Rect.fromCenter(
        center: position,
        width: textPainter.width + 12,
        height: textPainter.height + 6,
      );
      final bgPaint = Paint()..color = Colors.white.withValues(alpha: 0.95);
      final borderPaint = Paint()
        ..color = const Color(0xFFCBD5E1)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.0;
      canvas.drawRRect(RRect.fromRectAndRadius(rect, const Radius.circular(4)), bgPaint);
      canvas.drawRRect(RRect.fromRectAndRadius(rect, const Radius.circular(4)), borderPaint);
    }

    textPainter.paint(
      canvas,
      Offset(position.dx - textPainter.width / 2, position.dy - textPainter.height / 2),
    );
  }

  Offset _intersectRect(Offset origin, Offset target, Rect rect) {
    final dx = target.dx - origin.dx;
    final dy = target.dy - origin.dy;
    if (dx == 0 && dy == 0) return origin;

    final cx = rect.center.dx;
    final cy = rect.center.dy;
    final halfW = rect.width / 2;
    final halfH = rect.height / 2;

    double scaleX = dx != 0 ? halfW / dx.abs() : double.infinity;
    double scaleY = dy != 0 ? halfH / dy.abs() : double.infinity;
    double scale = math.min(scaleX, scaleY);

    return Offset(cx + dx * scale, cy + dy * scale);
  }

  double elementWidth(ElementDetailModel e) => e.ancho > 0 ? e.ancho : 240.0;

  double elementEstimatedHeight(ElementDetailModel e) {
    int lines = 3 + e.properties.attributes.length + e.properties.methods.length;
    return math.max(e.alto > 0 ? e.alto : 100.0, lines * 22.0 + 40.0);
  }

  @override
  bool shouldRepaint(covariant _UmlRelationsPainter oldDelegate) => true;
}

class _UmlElementCard extends StatelessWidget {
  final ElementDetailModel element;
  final VoidCallback onTap;
  final VoidCallback onDelete;
  final Function(double dx, double dy)? onPanUpdate;

  const _UmlElementCard({
    required this.element,
    required this.onTap,
    required this.onDelete,
    this.onPanUpdate,
  });

  Color _getHeaderColor(String type) {
    switch (type.toUpperCase()) {
      case 'INTERFAZ':
        return const Color(0xFF8B5CF6); // Purple
      case 'ABSTRACTA':
      case 'CLASE_ABSTRACTA':
        return const Color(0xFFF59E0B); // Amber
      case 'ENUMERACION':
      case 'ENUM':
        return const Color(0xFF10B981); // Green
      case 'PAQUETE':
        return const Color(0xFF64748B); // Slate
      case 'CLASE':
      default:
        return const Color(0xFF3B82F6); // Blue
    }
  }

  String _getStereotypeBadge(ElementDetailModel el) {
    if (el.properties.stereotype != null && el.properties.stereotype!.isNotEmpty) {
      return '<<${el.properties.stereotype}>>';
    }
    switch (el.tipoElemento.toUpperCase()) {
      case 'INTERFAZ':
        return '<<interface>>';
      case 'ABSTRACTA':
      case 'CLASE_ABSTRACTA':
        return '<<abstract>>';
      case 'ENUMERACION':
      case 'ENUM':
        return '<<enumeration>>';
      default:
        return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final headerColor = _getHeaderColor(element.tipoElemento);
    final stereotype = _getStereotypeBadge(element);

    return GestureDetector(
      onPanUpdate: onPanUpdate != null
          ? (details) => onPanUpdate!(details.delta.dx, details.delta.dy)
          : null,
      onTap: onTap,
      child: Material(
        elevation: 4,
        borderRadius: BorderRadius.circular(8),
        color: Colors.white,
        child: Container(
          width: element.ancho > 0 ? element.ancho : 240,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: headerColor.withValues(alpha: 0.6), width: 1.5),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              // Header
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: headerColor,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(6.5),
                    topRight: Radius.circular(6.5),
                  ),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (stereotype.isNotEmpty)
                            Text(
                              stereotype,
                              style: const TextStyle(
                                color: Colors.white70,
                                fontSize: 10,
                                fontStyle: FontStyle.italic,
                              ),
                            ),
                          Text(
                            element.nombre,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, color: Colors.white70, size: 18),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                      tooltip: 'Eliminar',
                      onPressed: onDelete,
                    ),
                  ],
                ),
              ),

              // Description if any
              if (element.properties.description != null &&
                  element.properties.description!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.all(6.0),
                  child: Text(
                    element.properties.description!,
                    style: const TextStyle(
                      fontSize: 11,
                      fontStyle: FontStyle.italic,
                      color: Color(0xFF64748B),
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),

              const Divider(height: 1, color: Color(0xFFE2E8F0)),

              // Attributes Compartment
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                color: const Color(0xFFF8FAFC),
                child: element.properties.attributes.isEmpty
                    ? const Text(
                        '(Sin atributos)',
                        style: TextStyle(color: Colors.black38, fontSize: 11, fontStyle: FontStyle.italic),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: element.properties.attributes.map((attr) {
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 1.5),
                            child: Row(
                              children: [
                                Text(
                                  _visSymbol(attr.visibility),
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                                ),
                                const SizedBox(width: 4),
                                Expanded(
                                  child: Text(
                                    '${attr.name}: ${attr.type}',
                                    style: const TextStyle(fontSize: 12, color: Color(0xFF1E293B)),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                          );
                        }).toList(),
                      ),
              ),

              const Divider(height: 1, color: Color(0xFFE2E8F0)),

              // Methods Compartment
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                child: element.properties.methods.isEmpty
                    ? const Text(
                        '(Sin métodos)',
                        style: TextStyle(color: Colors.black38, fontSize: 11, fontStyle: FontStyle.italic),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: element.properties.methods.map((method) {
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 1.5),
                            child: Row(
                              children: [
                                Text(
                                  _visSymbol(method.visibility),
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                                ),
                                const SizedBox(width: 4),
                                Expanded(
                                  child: Text(
                                    '${method.name}(${method.parameters}): ${method.returnType}',
                                    style: const TextStyle(fontSize: 12, color: Color(0xFF1E293B)),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ),
                          );
                        }).toList(),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _visSymbol(String vis) {
    switch (vis) {
      case 'PUBLIC':
      case '+':
        return '+';
      case 'PRIVATE':
      case '-':
        return '-';
      case 'PROTECTED':
      case '#':
        return '#';
      case 'PACKAGE':
      case '~':
        return '~';
      default:
        return '+';
    }
  }
}

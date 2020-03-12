//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.Utf8Appendable.NotUtf8Exception;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * URI Utility methods.
 * <p>
 * This class assists with the decoding and encoding or HTTP URI's.
 * It differs from the java.net.URL class as it does not provide
 * communications ability, but it does assist with query string
 * formatting.
 * </p>
 *
 * @see UrlEncoded
 */
public class OldURIUtil
        implements Cloneable
{
    private static final Logger LOG = Log.getLogger(OldURIUtil.class);

    private OldURIUtil()
    {}

    /* ------------------------------------------------------------ */
    /* Decode a URI path and strip parameters
     */
    public static String decodePath(String path)
    {
        return decodePath(path,0,path.length());
    }

    /* ------------------------------------------------------------ */
    /* Decode a URI path and strip parameters of UTF-8 path
     */
    public static String decodePath(String path, int offset, int length)
    {
        try
        {
            Utf8StringBuilder builder=null;
            int end=offset+length;
            for (int i=offset;i<end;i++)
            {
                char c = path.charAt(i);
                switch(c)
                {
                    case '%':
                        if (builder==null)
                        {
                            builder=new Utf8StringBuilder(path.length());
                            builder.append(path,offset,i-offset);
                        }
                        if ((i+2)<end)
                        {
                            char u=path.charAt(i+1);
                            if (u=='u')
                            {
                                // TODO this is wrong. This is a codepoint not a char
                                builder.append((char)(0xffff&TypeUtil.parseInt(path,i+2,4,16)));
                                i+=5;
                            }
                            else
                            {
                                builder.append((byte)(0xff&(TypeUtil.convertHexDigit(u)*16+TypeUtil.convertHexDigit(path.charAt(i+2)))));
                                i+=2;
                            }
                        }
                        else
                        {
                            throw new IllegalArgumentException("Bad URI % encoding");
                        }

                        break;

                    case ';':
                        if (builder==null)
                        {
                            builder=new Utf8StringBuilder(path.length());
                            builder.append(path,offset,i-offset);
                        }

                        while(++i<end)
                        {
                            if (path.charAt(i)=='/')
                            {
                                builder.append('/');
                                break;
                            }
                        }

                        break;

                    default:
                        if (builder!=null)
                            builder.append(c);
                        break;
                }
            }

            if (builder!=null)
                return builder.toString();
            if (offset==0 && length==path.length())
                return path;
            return path.substring(offset,end);
        }
        catch(NotUtf8Exception e)
        {
            LOG.warn(path.substring(offset,offset+length)+" "+e);
            LOG.debug(e);
            return decodeISO88591Path(path,offset,length);
        }
    }


    /* ------------------------------------------------------------ */
    /* Decode a URI path and strip parameters of ISO-8859-1 path
     */
    private static String decodeISO88591Path(String path, int offset, int length)
    {
        StringBuilder builder=null;
        int end=offset+length;
        for (int i=offset;i<end;i++)
        {
            char c = path.charAt(i);
            switch(c)
            {
                case '%':
                    if (builder==null)
                    {
                        builder=new StringBuilder(path.length());
                        builder.append(path,offset,i-offset);
                    }
                    if ((i+2)<end)
                    {
                        char u=path.charAt(i+1);
                        if (u=='u')
                        {
                            // TODO this is wrong. This is a codepoint not a char
                            builder.append((char)(0xffff&TypeUtil.parseInt(path,i+2,4,16)));
                            i+=5;
                        }
                        else
                        {
                            builder.append((byte)(0xff&(TypeUtil.convertHexDigit(u)*16+TypeUtil.convertHexDigit(path.charAt(i+2)))));
                            i+=2;
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException();
                    }

                    break;

                case ';':
                    if (builder==null)
                    {
                        builder=new StringBuilder(path.length());
                        builder.append(path,offset,i-offset);
                    }
                    while(++i<end)
                    {
                        if (path.charAt(i)=='/')
                        {
                            builder.append('/');
                            break;
                        }
                    }
                    break;


                default:
                    if (builder!=null)
                        builder.append(c);
                    break;
            }
        }

        if (builder!=null)
            return builder.toString();
        if (offset==0 && length==path.length())
            return path;
        return path.substring(offset,end);
    }
}
